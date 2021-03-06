package br.univali.ps.atualizador;

import br.univali.ps.nucleo.Configuracoes;
import br.univali.ps.nucleo.NamedThreadFactory;
import br.univali.ps.nucleo.PortugolStudio;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 *
 * @author Luiz Fernando Noschang
 */
public final class GerenciadorAtualizacoes
{
    private static final int INTERVALO_ENTRE_TENTATIVAS_ATUALIZACAO = 10000; // 10 segundos
    private static final int HTTP_OK = 200;

    private static final Logger LOGGER = Logger.getLogger(GerenciadorAtualizacoes.class.getName());
    private static final ExecutorService servicoThread = Executors.newSingleThreadExecutor(new NamedThreadFactory("Portugol Studio (Gerenciador de atualizações)"));

    private final File caminhoScriptAtualizacaoLocal = new File(Configuracoes.getInstancia().getDiretorioInstalacao(), "atualizacao.script");
    private final File caminhoScriptAtualizacaoTemporario = new File(Configuracoes.getInstancia().getDiretorioInstalacao(), "atualizacao.script.temp");

    private final File caminhoInicializadorLocal = new File(Configuracoes.getInstancia().getDiretorioInstalacao(), "inicializador-ps.jar");
    private final File caminhoInicializadorTemporario = new File(Configuracoes.getInstancia().getDiretorioTemporario(), "inicializador-ps.jar");
    private final File caminhoInicializadorAntigo = new File(Configuracoes.getInstancia().getDiretorioInstalacao(), "inicializador-ps-antigo.jar");

    private String caminhoScriptAtualizacaoRemoto;
    private String caminhoHashScriptAtualizacaoRemoto;
    
    private boolean executando = false;
    private boolean uriModificada = false;
    private ObservadorAtualizacao observadorAtualizacao;

    private String uriAtualizacao;
    private String caminhoInicializadorRemoto;
    private String caminhoHashInicializadorRemoto;

    private Map<String, String> caminhosInstalacao;
    private Map<String, String> caminhosRemotos;

    public GerenciadorAtualizacoes()
    {
        setUriAtualizacao(Configuracoes.getInstancia().getUriAtualizacao());
        setUriAtualizacaoModificada(false);
    }

    private Map<String, String> criarMapaCaminhosInstalacao()
    {
        Configuracoes configuracoes = Configuracoes.getInstancia();
        Map<String, String> mapaCaminhosInstalacao = new HashMap<>();

        mapaCaminhosInstalacao.put("ajuda", configuracoes.getDiretorioAjuda().getAbsolutePath());
        mapaCaminhosInstalacao.put("exemplos", configuracoes.getDiretorioExemplos().getAbsolutePath());
        mapaCaminhosInstalacao.put("plugins", configuracoes.getDiretorioPlugins().getAbsolutePath());
        mapaCaminhosInstalacao.put("aplicacao", configuracoes.getDiretorioAplicacao().getAbsolutePath());
        mapaCaminhosInstalacao.put("bibliotecas", configuracoes.getDiretorioBibliotecas().getAbsolutePath());

        return mapaCaminhosInstalacao;
    }

    private Map<String, String> criarMapaCaminhosRemotos()
    {
        Map<String, String> mapaCaminhosRemotos = new HashMap<>();

        mapaCaminhosRemotos.put("ajuda", uriAtualizacao.concat("/ajuda"));
        mapaCaminhosRemotos.put("exemplos", uriAtualizacao.concat("/exemplos"));
        mapaCaminhosRemotos.put("plugins", uriAtualizacao.concat("/plugins"));
        mapaCaminhosRemotos.put("bibliotecas", uriAtualizacao.concat("/bibliotecas"));
        mapaCaminhosRemotos.put("aplicacao", uriAtualizacao.concat("/aplicacao"));

        return mapaCaminhosRemotos;
    }

    public void baixarAtualizacoes()
    {
        if (!isExecutando())
        {
            setExecutando(true);

            servicoThread.execute(new Atualizacao());
        }
    }

    private synchronized void setExecutando(boolean executando)
    {
        this.executando = executando;
    }

    private synchronized boolean isExecutando()
    {
        return executando;
    }

    synchronized void verificarUriAtualizacao() throws IOException
    {
        if (uriModificada)
        {
            throw new IOException("A URI de atualizaçao foi modificada");
        }
    }

    private synchronized boolean isUriAtualizacaoModificada()
    {
        return uriModificada;
    }

    private synchronized void setUriAtualizacaoModificada(boolean modificada)
    {
        this.uriModificada = modificada;
    }

    public void setUriAtualizacao(String uriAtualizacao)
    {
        this.uriAtualizacao = uriAtualizacao;

        this.caminhoInicializadorRemoto = uriAtualizacao.concat("/inicializador-ps.jar");
        this.caminhoHashInicializadorRemoto = uriAtualizacao.concat("/inicializador-ps.hash");
        
        this.caminhoScriptAtualizacaoRemoto = uriAtualizacao.concat("/atualizacao.script");
        this.caminhoHashScriptAtualizacaoRemoto = uriAtualizacao.concat("/atualizacao.hash");

        this.caminhosInstalacao = criarMapaCaminhosInstalacao();
        this.caminhosRemotos = criarMapaCaminhosRemotos();

        setUriAtualizacaoModificada(isExecutando());
        baixarAtualizacoes();
    }

    public void setObservadorAtualizacao(ObservadorAtualizacao observadorAtualizacao)
    {
        this.observadorAtualizacao = observadorAtualizacao;
    }

    private final class Atualizacao implements Runnable
    {
        private final List<TarefaAtualizacao> tarefas = new ArrayList<>();

        private boolean houveAtualizacoes = false;
        private boolean houveFalhaAtualizacao = false;

        @Override
        public void run()
        {
            try (CloseableHttpClient clienteHttp = HttpClients.createDefault())
            {
                do
                {
                    try
                    {
                        houveFalhaAtualizacao = false;

                        verificarUriAtualizacao();

                        criarTarefasAtualizacao(clienteHttp);
                        executarTarefasAtualizacao(clienteHttp);
                    }
                    catch (IOException | ZipException excecao)
                    {
                        capturarFalhaAtualizacao(excecao);
                    }
                }
                while (houveFalhaAtualizacao);
            }
            catch (IOException excecao)
            {
                LOGGER.log(Level.WARNING, String.format("Erro ao fechar o cliente HTTP: %s", excecao.getMessage()), excecao);
            }

            setExecutando(false);
            notificarConclusaoAtualizacao();
        }

        private void capturarFalhaAtualizacao(Throwable excecao)
        {
            houveFalhaAtualizacao = true;

            if (isUriAtualizacaoModificada())
            {
                setUriAtualizacaoModificada(false);
            }

            LOGGER.log(Level.WARNING, String.format("Erro ao atualizar o Portugol Studio: %s", excecao.getMessage()), excecao);

            if (Configuracoes.getInstancia().getDiretorioTemporario().exists())
            {
                FileUtils.deleteQuietly(Configuracoes.getInstancia().getDiretorioTemporario());
            }

            try
            {
                Thread.sleep(INTERVALO_ENTRE_TENTATIVAS_ATUALIZACAO); // Aguarda alguns segundos antes da próxima tentativa
            }
            catch (InterruptedException ex)
            {

            }
        }

        private void notificarConclusaoAtualizacao()
        {
            if (observadorAtualizacao != null && houveAtualizacoes)
            {
                observadorAtualizacao.atualizacaoConcluida();
            }
        }

        private void executarTarefasAtualizacao(CloseableHttpClient clienteHttp) throws IOException, ZipException
        {
            houveAtualizacoes = false;

            prepararDiretorioTemporario();

            for (TarefaAtualizacao tarefa : tarefas)
            {
                PortugolStudio.getInstancia().getGerenciadorAtualizacoes().verificarUriAtualizacao();

                if (tarefa.precisaAtualizar())
                {
                    tarefa.baixarAtualizacao();
                    houveAtualizacoes = true;
                }
            }

            PortugolStudio.getInstancia().getGerenciadorAtualizacoes().verificarUriAtualizacao();

            manterCompatibilidade(clienteHttp);
            atualizarInicializador(clienteHttp);
        }

        private void manterCompatibilidade(CloseableHttpClient clienteHttp) throws IOException
        {
            /* 
             * Este trecho deve ser mantido para compatibilidade com a versão antiga do inicializador e do atualizador. 
             *
             * Se for removido, não será possível fazer downgrade para a versão anterior após atualizar para a
             * versão de testes usando o comando "BETA" na tela incial.
             */
            if (houveAtualizacoes)
            {
                Util.baixarArquivoRemoto(caminhoScriptAtualizacaoRemoto, caminhoScriptAtualizacaoTemporario, clienteHttp);

                validarScriptAtualizacao(clienteHttp);
            }
        }

        private void validarScriptAtualizacao(CloseableHttpClient clienteHttp) throws IOException
        {
            try
            {
                String hashAtualizacaoLocal = Util.calcularHashArquivo(caminhoScriptAtualizacaoTemporario);
                String hashAtualizacaoRemoto = Util.obterHashRemoto(caminhoHashScriptAtualizacaoRemoto, clienteHttp);

                if (!hashAtualizacaoLocal.equals(hashAtualizacaoRemoto))
                {
                    throw new IOException("O script de atualização do Portugol Studio não foi baixado corretamente");
                }
                else
                {
                    caminhoScriptAtualizacaoTemporario.renameTo(caminhoScriptAtualizacaoLocal);
                }
            }
            catch (IOException excecao)
            {
                FileUtils.deleteQuietly(caminhoScriptAtualizacaoTemporario);

                throw excecao;
            }
        }

        private void atualizarInicializador(CloseableHttpClient clienteHttp) throws IOException
        {
            String hashInicializadorLocal = Util.calcularHashArquivo(caminhoInicializadorLocal);
            String hashInicializadorRemoto = Util.obterHashRemoto(caminhoHashInicializadorRemoto, clienteHttp);

            PortugolStudio.getInstancia().getGerenciadorAtualizacoes().verificarUriAtualizacao();

            if (!hashInicializadorLocal.equals(hashInicializadorRemoto))
            {
                FileUtils.deleteQuietly(caminhoInicializadorAntigo);
                caminhoInicializadorTemporario.getParentFile().mkdirs();
                
                Util.baixarArquivoRemoto(caminhoInicializadorRemoto, caminhoInicializadorTemporario, clienteHttp);

                validarInicializador(clienteHttp);
                instalarInicializador();
            }
        }

        private void validarInicializador(CloseableHttpClient clienteHttp) throws IOException
        {
            try
            {
                String hashInicializadorLocal = Util.calcularHashArquivo(caminhoInicializadorTemporario);
                String hashInicializadorRemoto = Util.obterHashRemoto(caminhoHashInicializadorRemoto, clienteHttp);

                if (!hashInicializadorLocal.equals(hashInicializadorRemoto))
                {
                    throw new IOException("O script de atualização do Portugol Studio não foi baixado corretamente");
                }
            }
            catch (IOException excecao)
            {
                FileUtils.deleteQuietly(caminhoInicializadorTemporario);

                throw excecao;
            }
        }

        private void instalarInicializador() throws IOException
        {
            PortugolStudio.getInstancia().setAtualizandoInicializador(true);

            int step = 0;

            try
            {
                step++;
                FileUtils.moveFile(caminhoInicializadorLocal, caminhoInicializadorAntigo);

                step++;
                FileUtils.moveFile(caminhoInicializadorTemporario, caminhoInicializadorLocal);

                step++;
                houveAtualizacoes = true;
            }
            catch (IOException ex)
            {
                try
                {
                    if (step >= 2)
                    {
                        FileUtils.deleteQuietly(caminhoInicializadorLocal);
                        FileUtils.deleteQuietly(caminhoInicializadorTemporario);
                        FileUtils.moveFile(caminhoInicializadorAntigo, caminhoInicializadorLocal);
                    }
                }
                catch (IOException ex1)
                {
                    Logger.getLogger(GerenciadorAtualizacoes.class.getName()).log(Level.SEVERE, null, ex1);
                }

                PortugolStudio.getInstancia().setAtualizandoInicializador(false);

                throw ex;
            }

            PortugolStudio.getInstancia().setAtualizandoInicializador(false);
        }

        private void criarTarefasAtualizacao(CloseableHttpClient clienteHttp)
        {
            tarefas.clear();

            criarTarefaAtualizacaoRecurso("ajuda", clienteHttp);
            criarTarefaAtualizacaoRecurso("exemplos", clienteHttp);
            criarTarefaAtualizacaoRecurso("aplicacao", clienteHttp);

            criarTarefasAtualizacaoPlugins(clienteHttp);
        }

        private void criarTarefaAtualizacaoRecurso(String recurso, CloseableHttpClient clienteHttp)
        {
            String caminhoRemoto = caminhosRemotos.get(recurso);
            File caminhoInstalacao = new File(caminhosInstalacao.get(recurso));
            File caminhoTemporario = new File(Configuracoes.getInstancia().getDiretorioTemporario(), recurso);

            tarefas.add(new TarefaAtualizacao(caminhoRemoto, caminhoInstalacao, caminhoTemporario, clienteHttp));
        }

        private void criarTarefasAtualizacaoPlugins(CloseableHttpClient clienteHttp)
        {
            String uriAutoInstalacao = uriAtualizacao.concat("/").concat("plugins.auto");
            List<String> pluginsAtualizar = lerArquivoAutoInstalacao(uriAutoInstalacao, clienteHttp);

            File diretorioPlugins = Configuracoes.getInstancia().getDiretorioPlugins();

            if (diretorioPlugins.exists())
            {
                for (File caminho : diretorioPlugins.listFiles())
                {
                    if (!pluginsAtualizar.contains(caminho.getName()))
                    {
                        pluginsAtualizar.add(caminho.getName());
                    }
                }
            }

            for (String nomePlugin : pluginsAtualizar)
            {
                String caminhoRemoto = caminhosRemotos.get("plugins").concat("/").concat(nomePlugin);
                String pacotePlugin = caminhoRemoto.concat("/pacote.zip");
                String hashPlugin = caminhoRemoto.concat("/hash");

                if (Util.caminhoRemotoExiste(pacotePlugin, clienteHttp) && Util.caminhoRemotoExiste(hashPlugin, clienteHttp))
                {
                    File caminhoInstalacao = new File(caminhosInstalacao.get("plugins"), nomePlugin);
                    File caminhoTemporario = new File(new File(Configuracoes.getInstancia().getDiretorioTemporario(), "plugins"), nomePlugin);

                    tarefas.add(new TarefaAtualizacao(caminhoRemoto, caminhoInstalacao, caminhoTemporario, clienteHttp));
                }
            }
        }

        private void prepararDiretorioTemporario()
        {
            Configuracoes configuracoes = Configuracoes.getInstancia();
            File diretorioTemporario = configuracoes.getDiretorioTemporario();

            if (diretorioTemporario.exists())
            {
                FileUtils.deleteQuietly(diretorioTemporario);
            }

            if (caminhoScriptAtualizacaoLocal.exists())
            {
                FileUtils.deleteQuietly(caminhoScriptAtualizacaoLocal);
            }

            if (caminhoScriptAtualizacaoTemporario.exists())
            {
                FileUtils.deleteQuietly(caminhoScriptAtualizacaoTemporario);
            }

            if (caminhoInicializadorTemporario.exists())
            {
                FileUtils.deleteQuietly(caminhoInicializadorTemporario);
            }
        }

        private List<String> lerArquivoAutoInstalacao(String uriArquivoAutoInstalacao, CloseableHttpClient clienteHttp)
        {
            List<String> entradas = new ArrayList<>();

            HttpGet httpGet = new HttpGet(uriArquivoAutoInstalacao);

            try (CloseableHttpResponse resposta = clienteHttp.execute(httpGet))
            {
                final int resultado = resposta.getStatusLine().getStatusCode();

                if (resultado == HTTP_OK)
                {
                    String linha;

                    try (InputStream is = resposta.getEntity().getContent(); BufferedReader leitor = new BufferedReader(new InputStreamReader(is)))
                    {
                        while ((linha = leitor.readLine()) != null)
                        {
                            linha = linha.trim();

                            if (linha.length() > 0 && !linha.startsWith("#"))
                            {
                                entradas.add(linha);
                            }
                        }
                    }
                }
            }
            catch (IOException excecao)
            {

            }

            return entradas;
        }
    }
}

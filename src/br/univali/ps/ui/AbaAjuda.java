/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * AbaAjuda.java
 *
 * Created on 28/09/2011, 20:13:33
 */
package br.univali.ps.ui;

import br.univali.ps.ui.util.IconFactory;
import java.io.File;
import java.net.URL;
import javax.swing.JTabbedPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 *
 * @author fillipipelz
 */
public class AbaAjuda extends Aba implements HyperlinkListener, AbaListener{

    /** Creates new form AbaAjuda */
    public AbaAjuda(JTabbedPane painelTabulado) {
        super(painelTabulado);
        cabecalho.setTitulo("Ajuda");
        cabecalho.setIcone(IconFactory.createIcon(IconFactory.CAMINHO_ICONES_PEQUENOS, "information.png"));
        initComponents();
        adicionarAbaListener(this);
        jTextPane1.getDocument().putProperty("IgnoreCharsetDirective", Boolean.TRUE);
        this.jTextPane1.addHyperlinkListener(this);
        displayPage("help/index.html");
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTextPane1 = new javax.swing.JTextPane();

        setLayout(new java.awt.BorderLayout());

        jTextPane1.setContentType("text/html");
        jTextPane1.setEditable(false);
        jScrollPane1.setViewportView(jTextPane1);

        add(jScrollPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextPane jTextPane1;
    // End of variables declaration//GEN-END:variables

     @Override
    public void hyperlinkUpdate(HyperlinkEvent he) {
        if (he.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            try {
// Loads the new page represented by link clicked
                URL url = he.getURL();               
                jTextPane1.setPage(url);                
            } catch (Exception exc) {
            }
        }
    }

    public void displayPage(String page) {

// Check if user has specified any command line parameter
        if (page != null && page.trim().length() > 0) {

            /* User may specify one of the following
            1. A relative path for a local file
            2. An absolute path for a local file
            3. A URL
            Check for a valid user input
             */

            File localFile = new File(page);

            // Chgeck if the file exists on the dist
            if (localFile.exists() && localFile.isFile()) {
                /* Check if user specified the absolute path
                Add the file protocol in front of file name */

                page = "file:///" + localFile.getAbsolutePath();
                try {
                    jTextPane1.setPage(page);
                } catch (Exception e1) {
                    // Not a valid URL
                    jTextPane1.setText("Could not load page:" + page + "\n"
                            + "Error:" + e1.getMessage());
                }
            }
        }
    }

    public boolean fechandoAba(Aba aba) {
        return true;
    }
}
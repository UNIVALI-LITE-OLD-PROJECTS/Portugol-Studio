package br.univali.ps.ui.rstautil.tree;

final class ProgramaTreeNode extends SourceTreeNode
{
    
    public ProgramaTreeNode()
    {
        super("programa");        
    }
    
    @Override
    Object aceitar(OutlineTreeVisitor visitor)
    {
        return visitor.visitar(this);
    }
}
/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.jasm16.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.jasm16.ast.ASTNode;
import de.codesourcery.jasm16.ast.ASTVisitor;
import de.codesourcery.jasm16.ast.CharacterLiteralNode;
import de.codesourcery.jasm16.ast.CommentNode;
import de.codesourcery.jasm16.ast.ExpressionNode;
import de.codesourcery.jasm16.ast.IASTVisitor;
import de.codesourcery.jasm16.ast.IIterationContext;
import de.codesourcery.jasm16.ast.IncludeBinaryFileNode;
import de.codesourcery.jasm16.ast.IncludeSourceFileNode;
import de.codesourcery.jasm16.ast.InitializedMemoryNode;
import de.codesourcery.jasm16.ast.InstructionNode;
import de.codesourcery.jasm16.ast.LabelNode;
import de.codesourcery.jasm16.ast.NumberNode;
import de.codesourcery.jasm16.ast.ObjectCodeOutputNode;
import de.codesourcery.jasm16.ast.OperandNode;
import de.codesourcery.jasm16.ast.OperatorNode;
import de.codesourcery.jasm16.ast.OriginNode;
import de.codesourcery.jasm16.ast.RegisterReferenceNode;
import de.codesourcery.jasm16.ast.StatementNode;
import de.codesourcery.jasm16.ast.SymbolReferenceNode;
import de.codesourcery.jasm16.ast.UninitializedMemoryNode;
import de.codesourcery.jasm16.ast.UnparsedContentNode;
import de.codesourcery.jasm16.compiler.ICompilationContext;
import de.codesourcery.jasm16.compiler.Label;
import de.codesourcery.jasm16.compiler.io.AbstractObjectCodeWriter;

/**
 * {@link IASTVisitor} implementation that outputs
 * formatted source code for an AST along with the generated
 * object code as comments.
 * 
 * @author tobias.gierke@code-sourcery.de
 */
public class FormattingVisitor extends ASTVisitor {

    private final int column0Width = 40;

    private final ICompilationContext context;

    public FormattingVisitor(ICompilationContext context) {
        this.context = context;
    }

    protected void output(String s) {
        System.out.print( s );
    }
    

    
    public void visit(IncludeSourceFileNode node, IIterationContext itContext) 
    {
    	output( "include \""+node.getResourceIdentifier()+"\"" );
    	itContext.dontGoDeeper();
    }
    
    
    public void visit(CharacterLiteralNode node, IIterationContext context) 
    {
    }

    
    public void visit(CommentNode node, IIterationContext context) 
    {
    	if ( node.getParent().getChildCount() == 1 ) {
    		output( getSource( node ) );
    	}
    }
    
    private String getSource(ASTNode node) 
    {
    	if ( node.getTextRegion() == null ) {
    		return "<no text range on node "+node.getClass().getSimpleName()+">";
    	}
    	try {
			return context.getCurrentCompilationUnit().getSource( node.getTextRegion() ).replace("\t", " ").replace("\r","").replace("\n","").trim();
		} catch (IOException e) {
			return "<IO exception when reading text from node "+node.getClass().getSimpleName();
		}
    }

    
    public void visit(ExpressionNode node, IIterationContext context) {
    }
    
	protected StatementNode getStatementNode(ASTNode node) 
	{
		ASTNode current = node;
		while ( current.getParent() != null ) {
			if ( current instanceof StatementNode ) {
				return (StatementNode) current;
			}
			current = current.getParent();
		}
		return null;
	}    
	
	private String toString(LabelNode node) 
	{
        final Label symbol = node.getLabel();
        String address = "";
        if ( symbol != null && symbol.getAddress() != null ) 
        {
			address = " (0x"+Misc.toHexString( symbol.getAddress().getValue() )+")";
        }
        final String src = getSource( node );
        return Misc.padRight( src+address  , column0Width );		
	}

    
    public void visit(InstructionNode node, IIterationContext context) 
    {
        final LabelNode label = getLabelNode( node );
        final String labelText= label != null ? toString( label ) : "";

        final StringBuilder result = new StringBuilder();
        if ( label == null ) {
        	result.append( StringUtils.repeat(" " , column0Width ) );
        } 
        result.append( node.getOpCode().getIdentifier()+" ");

        final List<OperandNode> operands = node.getOperands();
        for (Iterator<OperandNode> it = operands.iterator(); it.hasNext();) 
        {
            final OperandNode operandNode = it.next();
            String sourceCode;
            try {
                final ITextRegion range = operandNode.getTextRegion();
                if ( range != null ) {
                    sourceCode = this.context.getCurrentCompilationUnit().getSource( range ).replaceAll("\t" , " " ).trim();
                } else {
                    sourceCode = "<no text range available>";
                }
            } catch (IOException e) {
                sourceCode = "<could not read source: "+e.getMessage()+">";
            }
            result.append( sourceCode );
            if ( it.hasNext() ) {
                result.append(",");
            }
        }

        final int width = 60-labelText.length();
        final String txt = Misc.padRight( result.toString() , width );
        if ( label != null ) {
        	output( txt );
        } else {
        	output( txt );
        }

        final HexStringWriter writer = new HexStringWriter(true);
        for (ObjectCodeOutputNode out : getStatementNode(node).getObjectOutputNodes()) 
        {
            try {
                out.writeObjectCode( writer, this.context );
            } catch (Exception e) { /* ok */ }
        }
        output("; "+writer.toString() );
    }

    private static class HexStringWriter extends AbstractObjectCodeWriter {

        private final StringBuilder builder = new StringBuilder();
        private final List<Byte> buffer = new ArrayList<Byte>();
        private final boolean printFirstWordAsBinaryLiteral;
        
        private boolean firstWord = true;
        
        public HexStringWriter(boolean printFirstWordAsBinaryLiteral) {
            this.printFirstWordAsBinaryLiteral = printFirstWordAsBinaryLiteral;
        }
        
        private void flushBuffer(boolean force) 
        {
            while ( ( force && ! buffer.isEmpty() ) || buffer.size() >= 2 ) 
            {
                byte val1 = buffer.remove(0);
                if ( ! buffer.isEmpty() ) {
                    byte val2 = buffer.remove(0);
                    if ( firstWord && printFirstWordAsBinaryLiteral ) {
                        final int word = ( val1 << 8 ) | toUnsignedInt( val2 );
                        builder.append( "(").append( Misc.toBinaryString( word , 16 ) ).append(") ");
                    }
                    builder.append( Misc.toHexString( val1 ) ).append( Misc.toHexString( val2 ) ).append(" ");
                    firstWord = false;
                }
                else 
                {
                    if ( firstWord && printFirstWordAsBinaryLiteral ) {
                        final int word = toUnsignedInt( val1 ); 
                        builder.append( "(").append( Misc.toBinaryString( word , 16 ) ).append(") ");                        
                    } 
                    builder.append( Misc.toHexString( val1 ) );
                    firstWord = false;                    
                }
            }
        }
        
        private int toUnsignedInt(byte b) {
            return b >= 0 ? b : b+256;
        }

        
        public String toString() 
        { 
            flushBuffer(true);
            return builder.toString();
        }

        
        protected void closeHook() throws IOException { }

        
        protected OutputStream createOutputStream() throws IOException
        {
            return new OutputStream() {

                
                public void write(int b) throws IOException
                {
                    buffer.add( (byte) ( b & 0xff ) );
                    flushBuffer(false);
                }
            };
        }

        
        protected void deleteOutputHook() throws IOException { }
    }

    
    public void visit(LabelNode node, IIterationContext context) 
    {
		output( toString( node ) );
    }
    
    private LabelNode getLabelNode(ASTNode node) {
    	
    	ASTNode current = node;
    	while ( ! (current instanceof StatementNode) && current.getParent() != null ) {
    		current = current.getParent();
    	}
    	if ( current instanceof StatementNode) 
    	{
    		for ( ASTNode child : current.getChildren() ) {
    			if ( child instanceof LabelNode ) {
    				return (LabelNode) child;
    			}
    		}
    	}
    	return null;
    }

    
    public void visit(SymbolReferenceNode node, IIterationContext context) {
    }

    
    public void visit(NumberNode node, IIterationContext context) {
    }

    
    public void visit(OperandNode node, IIterationContext context) 
    {
    }
    
    
    public void visit(IncludeBinaryFileNode node, IIterationContext context) {
    	output( getSource( node ) );
    }

    
    public void visit(OriginNode node, IIterationContext context) {
    	output( getSource( node ) );
    }
    
    
    public void visit(OperatorNode node, IIterationContext context) {
    }

    
    public void visit(RegisterReferenceNode node, IIterationContext context) {
    }

    
    public void visit(StatementNode node, IIterationContext context) 
    {
   		output("\n");
    }

    
    public void visit(InitializedMemoryNode node , IIterationContext context) {

        final HexStringWriter writer = new HexStringWriter(false);

        try {
            node.writeObjectCode( writer, this.context );
        } catch (Exception e) { /* ok */ }

        output( getSource( node ) );
        output("; "+writer.toString()  );	    
    }

    
    public void visit(UninitializedMemoryNode node, IIterationContext context) {
        output( getSource( node ) );			
    }

    
    public void visit(UnparsedContentNode node, IIterationContext context) {
        output( getSource( node ) );			
    }

}

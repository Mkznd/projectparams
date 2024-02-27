package org.projectparams.annotationprocessing.astcommons.parsing.expressions.named.selectable.fieldaccess;

import com.sun.tools.javac.tree.JCTree;
import org.projectparams.annotationprocessing.astcommons.context.ClassContext;
import org.projectparams.annotationprocessing.astcommons.parsing.expressions.Expression;
import org.projectparams.annotationprocessing.astcommons.parsing.expressions.ParameterizableObjectExpression;
import org.projectparams.annotationprocessing.astcommons.parsing.expressions.ParametrizedExpression;

import java.util.List;

public class ParametrizedFieldAccessExpression extends FieldAccessExpression implements ParameterizableObjectExpression {
    private final ParametrizedExpression parametrizedExpression;

    public ParametrizedFieldAccessExpression(String name, Expression owner, List<Expression> typeArguments) {
        super(name, owner);
        this.parametrizedExpression = new ParametrizedExpression(typeArguments, this);
    }

    @Override
    public JCTree.JCExpression toJcExpression() {
        return parametrizedExpression.toJcExpression();
    }

    @Override
    public void convertInnerIdentifiersToQualified(ClassContext classContext) {
        super.convertInnerIdentifiersToQualified(classContext);
        parametrizedExpression.convertInnerIdentifiersToQualified(classContext);
    }

    @Override
    public JCTree.JCExpression superToJcExpression() {
        return super.toJcExpression();
    }
}

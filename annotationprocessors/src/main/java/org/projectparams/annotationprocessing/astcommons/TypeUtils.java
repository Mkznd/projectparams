package org.projectparams.annotationprocessing.astcommons;

import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;
import org.projectparams.annotationprocessing.astcommons.invocabletree.NewClassInvocableTree;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Utility class for working with types
 * !!! THIS CLASS IS THE ONLY SOURCE OF TRUTH FOR TYPES !!!
 */
public class TypeUtils {
    private static Trees trees;
    private static JavacTypes types;
    private static Elements elements;
    private static Symtab symtab;
    private static Attr attr;
    private static Enter enter;
    private static MemberEnter memberEnter;

    // for some reason, types of NewClassTree nodes are not resolved during annotation processing
    // and any attempt to resolve them manually results in an error, while attribution does not affect types at all
    private static final Map<NewClassTree, String> effectiveConstructorOwnerTypeNames = new IdentityHashMap<>();


    // initialized in org.projectparams.annotationprocessing.MainProcessor
    public static void init(Trees trees, JavacTypes types, Elements elements, Symtab symtab, Attr attr, Enter enter,
                            MemberEnter memberEnter) {
        TypeUtils.trees = trees;
        TypeUtils.types = types;
        TypeUtils.elements = elements;
        TypeUtils.symtab = symtab;
        TypeUtils.attr = attr;
        TypeUtils.enter = enter;
        TypeUtils.memberEnter = memberEnter;
    }

    public static Type getTypeByName(String name) {
        return switch (name) {
            case "int" -> symtab.intType;
            case "long" -> symtab.longType;
            case "float" -> symtab.floatType;
            case "double" -> symtab.doubleType;
            case "boolean" -> symtab.booleanType;
            case "void" -> symtab.voidType;
            case "byte" -> symtab.byteType;
            case "short" -> symtab.shortType;
            case "char" -> symtab.charType;
            default -> {
                var typeElement = elements.getTypeElement(name);
                if (typeElement == null) {
                    throw new IllegalArgumentException("Cannot resolve type for " + name);
                }
                var type = types.getDeclaredType(typeElement);
                yield (Type) type;
            }
        };
    }


    public static String getBoxedTypeName(String name) {
        return switch (name) {
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "float" -> "java.lang.Float";
            case "double" -> "java.lang.Double";
            case "boolean" -> "java.lang.Boolean";
            case "void" -> "java.lang.Void";
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "char" -> "java.lang.Character";
            case "<any>" -> null;
            default -> name;
        };
    }

    public static TypeKind getTypeKind(TreePath path) {
        var type = trees.getTypeMirror(path);
        if (type == null) {
            return TypeKind.ERROR;
        }
        return trees.getTypeMirror(path).getKind();
    }

    public static String getOwnerTypeName(MethodInvocationTree invocation, TreePath path) {
        String ownerQualifiedName;
        if (invocation.getMethodSelect() instanceof MemberSelectTree memberSelectTree) {
            ownerQualifiedName = getOwnerNameFromMemberSelect(memberSelectTree, path);
        } else if (invocation.getMethodSelect() instanceof IdentifierTree) {
            ownerQualifiedName = getOwnerNameFromIdentifier(path);
        } else {
            throw new IllegalArgumentException("Unsupported method select type: "
                    + invocation.getMethodSelect().getClass().getCanonicalName());
        }
        return ownerQualifiedName;
    }

    public static String getOwnerTypeName(NewClassTree newClassTree) {
        var effectiveOwnerTypeName = effectiveConstructorOwnerTypeNames.get(newClassTree);
        if (effectiveOwnerTypeName != null) {
            return effectiveOwnerTypeName;
        }
        var ownerType = ((JCTree.JCExpression)newClassTree.getIdentifier()).type;
        if (ownerType != null) {
            return ownerType.toString();
        }
        return "<any>";
    }

    public static void addConstructorOwnerTypeName(NewClassTree newClassTree, String ownerTypeName) {
        effectiveConstructorOwnerTypeNames.put(newClassTree, ownerTypeName);
    }

    public static String getOwnerNameFromIdentifier(TreePath path) {
        while (!(path.getLeaf() instanceof ClassTree)) {
            path = path.getParentPath();
        }
        return getFullyQualifiedName((ClassTree) path.getLeaf());
    }

    public static String getFullyQualifiedName(ClassTree classTree) {
        JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) classTree;
        Element classElement = classDecl.sym;
        PackageElement packageElement = elements.getPackageOf(classElement);
        return packageElement.getQualifiedName().toString() + "." + classElement.getSimpleName().toString();
    }

    public static void attributeExpression(JCTree expression, TreePath methodTree) {
        var env = memberEnter.getMethodEnv(
                (JCTree.JCMethodDecl) methodTree.getLeaf(),
                enter.getClassEnv(((JCTree.JCClassDecl)getEnclosingClassPath(getEnclosingMethodPath(methodTree)).getLeaf()).sym)
        );
        attr.attribExpr(expression, env);
    }

    public static TreePath getEnclosingClassPath(TreePath path) {
        while (path != null && !(path.getLeaf() instanceof ClassTree)) {
            path = path.getParentPath();
        }
        if (path == null) {
            throw new IllegalArgumentException("Path is not enclosed in class");
        }
        return path;
    }

    public static TreePath getEnclosingMethodPath(TreePath path) {
        while (path != null && !(path.getLeaf() instanceof MethodTree)) {
            path = path.getParentPath();
        }
        if (path == null) {
            throw new IllegalArgumentException("Path is not enclosed in method");
        }
        return path;
    }

    private static String getOwnerNameFromMemberSelect(MemberSelectTree memberSelectTree, TreePath path) {
        var expression = memberSelectTree.getExpression();
        var ownerTree = trees.getTree(trees.getElement(new TreePath(path, expression)));
        String ownerQualifiedName = null;
        if (ownerTree != null) {
            switch (ownerTree) {
                case JCTree.JCClassDecl staticRef -> {
                    var ownerType = staticRef.sym.type;
                    if (ownerType != null) {
                        ownerQualifiedName = TypeUtils.getBoxedTypeName(ownerType.toString());
                    }
                }
                case JCTree.JCFieldAccess fieldAccess -> {
                    var ownerType = fieldAccess.selected.type;
                    if (ownerType != null) {
                        ownerQualifiedName = TypeUtils.getBoxedTypeName(ownerType.toString());
                    }
                }
                case JCTree.JCNewClass clazz -> {
                    var ownerType = clazz.type;
                    if (ownerType != null) {
                        ownerQualifiedName = TypeUtils.getBoxedTypeName(ownerType.toString());
                    }
                }
                case JCTree.JCMethodInvocation method -> {
                    var ownerType = method.meth.type.getReturnType();
                    if (ownerType != null) {
                        ownerQualifiedName = TypeUtils.getBoxedTypeName(ownerType.toString());
                    }
                }
                default ->
                        throw new IllegalArgumentException("Unsupported owner type: " + ownerTree.getClass().getCanonicalName());
            }
        } else {
            // in case owner is return type of fixed method, we won`t be able to access its tree
            // so retrieve type from method invocation manually
            if (expression instanceof JCTree.JCMethodInvocation methodInvocation) {
                if (methodInvocation.type != null) {
                    ownerQualifiedName = TypeUtils.getBoxedTypeName(methodInvocation.type.toString());
                }
            } else if (expression instanceof JCTree.JCNewClass newClass) {
                var ownerType = getOwnerTypeName(newClass);
                if (ownerType != null) {
                    ownerQualifiedName = TypeUtils.getBoxedTypeName(ownerType);
                } else {
                    var ownerTypeName = newClass.constructorType.tsym.type.toString();
                    if (ownerTypeName != null) {
                        ownerQualifiedName = TypeUtils.getBoxedTypeName(ownerTypeName);
                    }
                }
            }
        }
        return ownerQualifiedName;
    }
}

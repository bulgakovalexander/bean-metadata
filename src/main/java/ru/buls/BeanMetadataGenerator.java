package ru.buls;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.util.Arrays.asList;
import static javax.lang.model.SourceVersion.RELEASE_5;
import static javax.lang.model.element.ElementKind.*;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.BOOLEAN;
import static javax.tools.Diagnostic.Kind.WARNING;

/**
 * Created by alexander on 14.09.14.
 */
@SupportedSourceVersion(RELEASE_5)
@SupportedAnnotationTypes({"*"})
public class BeanMetadataGenerator extends AbstractProcessor {
    public static final String PREFIX = "P";
    public static final String STATIC_PREFIX = "S";
    private static final String BASE_CLASS = PREFIX + Object.class.getSimpleName();
    private static final String WRAP_METHOD = "w";
    private static final String _PREFIX = "_PREFIX";
    private static final String PARENT = "parent";
    private String intend = "    ";
    private static final String JAVA_LANG = "javax.metadata";

    String filter = null;
    private Collection<String> pkgs;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        Map<String, String> options = processingEnv.getOptions();
        this.filter = options.get("filter");
        String _tmp = options.get("package");
        if (_tmp != null) pkgs = Arrays.asList(_tmp.split(","));
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return (filter != null)
                ? new HashSet<String>(asList(filter))
                : super.getSupportedAnnotationTypes();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (roundEnv.processingOver() || annotations.size() == 0) {
            return false;
        }
        Set<? extends Element> elements = roundEnv.getRootElements();
        for (Element e : elements)
            if (asList(CLASS, INTERFACE).contains(e.getKind()))
                generate((TypeElement) e, elements, properties((TypeElement) e, elements));

        generateBaseClass();


        return false;
    }

    public void generate(TypeElement classElement, Set<? extends Element> elements, Map<String, TypeElement> properties) {
        Name qName = classElement.getQualifiedName();
        if (inPackage(qName)) {
            generate(classElement, elements, properties, false);
            generate(classElement, elements, properties, true);
        }
    }

    public boolean inPackage(Name qName) {
        if (pkgs == null) return true;
        for (String pkg : pkgs) if (qName.toString().startsWith(pkg)) return true;
        return false;
    }

    public void generate(TypeElement classElement, Set<? extends Element> elements,
                         Map<String, TypeElement> properties, boolean isStatic) {
        if (!isStatic /*&& isInterface*/) {
            generate(classElement, elements, properties, isStatic, true);
        }
        generate(classElement, elements, properties, isStatic, false);
    }

    protected static boolean isInterface(TypeElement classElement) {
        return ElementKind.INTERFACE.equals(classElement.getKind());
    }

    protected void generate(TypeElement classElement, Set<? extends Element> elements,
                            Map<String, TypeElement> properties, boolean isStatic, boolean isInterface) {
        Name className = classElement.getSimpleName();
        PackageElement packageElement = (PackageElement) classElement.getEnclosingElement();
        Name pkgName = packageElement.getQualifiedName();
        Filer processingEnvFiler = processingEnv.getFiler();
        String prefix = isStatic ? STATIC_PREFIX : PREFIX;
        String metadataClassName = prefix + className;
        BufferedWriter bw = null;
        try {
            processingEnv.getMessager().printMessage(WARNING,
                    "Generating " + pkgName + "." + metadataClassName);
            JavaFileObject jfo = processingEnvFiler.createSourceFile(pkgName + "." + metadataClassName);
            bw = new BufferedWriter(jfo.openWriter());
            bw.append("package ").append(pkgName).append(";");
            bw.newLine();

            bw.append("import " + JAVA_LANG + "." + BASE_CLASS + ";");
            bw.newLine();
            for (TypeElement elem : properties.values())
                if (elem != null) {
                    bw.append("import " + qualifiedMetadataName(elem, false) + ";");
                    bw.newLine();
                }

            TypeMirror _tmp = classElement.getSuperclass();
            DeclaredType superClass = _tmp instanceof DeclaredType ? (DeclaredType) _tmp : null;
            if (superClass == null && !(_tmp instanceof NoType))
                throw new IllegalStateException("incompatible base type " + superClass + " for " + classElement);

            bw.newLine();

            bw.append("public class ").append(metadataClassName);
            bw.append(" extends ");
//            if (superClass != null) {
//                TypeElement superElem = (TypeElement) superClass.asElement();
//                if (elements.contains(superElem)) bw.append(qualifiedMetadataName(superElem, isStatic));
//                else bw.append(JAVA_LANG + "." + BASE_CLASS);
//            } else
            bw.append(JAVA_LANG + "." + BASE_CLASS);
            bw.append(" {");
            bw.newLine();
            if (!isStatic) {
                bw.append(intend).append("public ").append(metadataClassName)
                        .append("(String prefix, " + BASE_CLASS + " parent) { super(prefix, parent); }");
                bw.newLine();
                bw.append(intend).append("protected ").append(metadataClassName).append("() { super(); }");
                bw.newLine();
            }

            bw.newLine();
            for (String result : properties.keySet()) {
                TypeElement elem = properties.get(result);

                if (elem != null) {
                    String type = metadataName(elem, false);
                    String _method = type + " " + result + "() { return " + " new " + type + "(\"" + result + "\", "
                            + (isStatic ? "null" : "this") + "); }";
                    bw.append(intend).append("public ");
                    if (isStatic) bw.append("static ");
                    bw.append(_method);
                    bw.newLine();
                }
                String _prop = "String " + result + " = " + w(result, !isStatic) + ";";
                bw.append(intend).append("public final ");
                if (isStatic) bw.append("static ");
                bw.append(_prop);
                bw.newLine();
            }

//            if (!isStatic) bw.append(intend).append("public static final ")
//                    .append(metadataClassName).append(" ").append(onlyUps(className.toString()).toLowerCase())
//                    .append(" = new ").append(metadataClassName).append("();");

            bw.newLine();
            bw.append("}");

        } catch (IOException e1) {
            throw new RuntimeException(e1);
        } finally {
            if (bw != null) try {
                bw.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private String getObjectClassName() {
        return PREFIX + Object.class.getSimpleName();
    }

    private void warning(String msg) {
        Messager messager = processingEnv.getMessager();
        messager.printMessage(WARNING, msg);
    }

    private Element get(TypeMirror what, Set<? extends Element> where) {
        Element result = null;
        if(what instanceof DeclaredType) {
            Element whatElem;
            DeclaredType dt = (DeclaredType) what;
            whatElem = dt.asElement();

            if (where.contains(whatElem)) {
                result = whatElem;
            }
        }


//        for (Element e : where) {
//            System.out.println(
//                    e.toString() + ", " + e.asType() + ", " + what);
//            if (e.equals(what)) {
//                System.out.println(
//                        e.toString() + " as " + e.asType() + " equals to " + what);
//                result = e;
//                break;
//            } else {
//                System.out.println(
//                        e.toString() + " as " + e.asType() + " not equals to " + what);
//            }
//
//        }
        return result;
    }

//    private static Symbol.TypeSymbol toSymbol(TypeMirror what) {
//        return ((Type.ClassType) what).asElement();
//    }

    private String w(String result, boolean wrap) {
        return wrap ? WRAP_METHOD + "(\"" + result + "\")" : "\"" + result + "\"";
    }

    private String onlyUps(String className) {
        StringBuilder builder = new StringBuilder();
        for (char c : className.toCharArray())
            if (Character.isUpperCase(c)) builder.append(c);
        return builder.length() != 0 ? builder.toString() : className;
    }

    public void generateBaseClass() {
        String metadataClassName = BASE_CLASS;
        String qualifiedBaseClass = JAVA_LANG + "." + metadataClassName;
        Filer processingEnvFiler = processingEnv.getFiler();
        BufferedWriter bw = null;

        processingEnv.getMessager().printMessage(WARNING,
                "Generating " + qualifiedBaseClass);

        try {
            JavaFileObject jfo = processingEnvFiler.createSourceFile(qualifiedBaseClass);

            bw = new BufferedWriter(jfo.openWriter());
            bw.append("package ").append(JAVA_LANG).append(";");
            bw.newLine();

            bw.append("public class " + metadataClassName + " implements CharSequence {");

            bw.newLine();
            bw.append(intend).append("protected final String " + _PREFIX + ";");
            bw.append(intend).append("protected final " + metadataClassName + " " + PARENT + ";");
            bw.newLine();
            bw.append(intend).append("public ").append(metadataClassName).append("(String prefix, "
                    + metadataClassName + " parent) { \n" +
                    intend + "this." + _PREFIX + " = prefix;\n" +
                    intend + "this." + PARENT + " = parent;\n" +
                    " }");
            bw.newLine();
            bw.append(intend).append("public ").append(metadataClassName).append("() { \n" +
                    intend + "this." + _PREFIX + " = \"\";\n" +
                    intend + "this." + PARENT + " = null;\n" +
                    " }");
            bw.newLine();

            bw.append(intend).append("public int length() { return " + _PREFIX + ".length(); }");
            bw.newLine();
            bw.append(intend).append("public char charAt(int index) { return " + _PREFIX + ".charAt(index); }");
            bw.newLine();
            bw.append(intend).append("public CharSequence subSequence(int start, int end) { return " + _PREFIX + ".subSequence(start, end); }");
            bw.newLine();

            bw.append(intend).append("public String toString() { return " + _PREFIX + "; }");
            bw.newLine();

//            bw.append(intend).append("public final String " + WRAP_METHOD
//                    + "(String p) { return " + _PREFIX + " != null ? " + _PREFIX + " +\".\" + p: p; }");


            bw.newLine();
            bw.append("    public final String w(String propName) {\n" +
                    "        PObject parent = this.parent;\n" +
                    "        StringBuilder prnt = new StringBuilder();\n" +
                    "        while (parent != null) {\n" +
                    "            prnt.append(parent._PREFIX).append(\".\");\n" +
                    "            parent = parent.parent;\n" +
                    "        }\n" +
                    "        return prnt.toString() + (_PREFIX != null ? _PREFIX + \".\" + propName : propName);\n" +
                    "    }");
            bw.newLine();
            bw.append("}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (bw != null) try {
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String metadataName(TypeElement elem, boolean isStatic) {
        return (isStatic ? STATIC_PREFIX : PREFIX) + elem.getSimpleName().toString();
    }

    public String qualifiedMetadataName(TypeElement elem, boolean isStatic) {
        return packageName(elem) + "." + metadataName(elem, isStatic);
    }

    public String packageName(TypeElement elem) {
        String s = elem.getSimpleName().toString();
        String q = elem.getQualifiedName().toString();
        return q.substring(0, q.length() - s.length() - 1);
    }

    public static Map<String, TypeElement> properties(TypeElement e, Set<? extends Element> elements) {
        Map<String, TypeElement> properties = new LinkedHashMap<String, TypeElement>();
        populate(properties, e, elements);
        TypeMirror sup = e.getSuperclass();
        while (sup instanceof DeclaredType) {
            DeclaredType sdt = (DeclaredType) sup;
            Element superE = sdt.asElement();
            if (superE != null && superE instanceof TypeElement
                    && !((TypeElement) superE).getQualifiedName().toString().equals(Object.class.getName())) {
                populate(properties, (TypeElement) superE, elements);
                sup = ((TypeElement) superE).getSuperclass();
            } else sup = null;
        }
        return properties;
    }

    public static void populate(Map<String, TypeElement> properties, TypeElement byElement, Set<? extends Element> elements) {
        for (Element childE : byElement.getEnclosedElements()) {

            Set<Modifier> modifiers = childE.getModifiers();
            boolean isPublic = modifiers.contains(PUBLIC);
            boolean isStatic = modifiers.contains(STATIC);

            String mName = childE.getSimpleName().toString();
            if (!isStatic && isPublic && asList(METHOD, FIELD).contains(childE.getKind())) {
                ExecutableElement exec = childE instanceof ExecutableElement ? (ExecutableElement) childE : null;
                VariableElement var = (exec != null) ? null : (VariableElement) childE;

                TypeMirror returnType = exec != null ? exec.getReturnType() : var.asType();
                TypeKind kind = returnType.getKind();
                String result = null;

                boolean isField = var != null;
                boolean noParams = isField || exec.getParameters().isEmpty();
                boolean booleanProperty = noParams && BOOLEAN == kind && returnType instanceof PrimitiveType
                        && (isField || (mName.startsWith("is") && mName.length() > 2
                        && mName.substring(2, 3).equals(mName.substring(2, 3).toUpperCase())));

                boolean defaultProperty = noParams && (isField || (mName.startsWith("get") && mName.length() > 3
                        && mName.substring(3, 4).equals(mName.substring(3, 4).toUpperCase())));

                if(isField) result = mName;
                else if (booleanProperty) result = decapitalize(mName.substring(2));
                else if (defaultProperty) result = decapitalize(mName.substring(3));

                if (result != null && !properties.containsKey(result)) {
                    TypeElement type = null;
                    if (defaultProperty && returnType instanceof DeclaredType) {
                        DeclaredType dType = (DeclaredType) returnType;
                        Element retElement = dType.asElement();
                        if (elements.contains(retElement)) type = (TypeElement) retElement;
                    }
                    properties.put(result, type);
                }
            }
        }
    }

    public static String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() > 1 && isUpperCase(name.charAt(1)) && isUpperCase(name.charAt(0))) return name;
        char chars[] = name.toCharArray();
        chars[0] = toLowerCase(chars[0]);
        return new String(chars);
    }
}

package ru.buls;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
@SupportedAnnotationTypes({"*"/*,"javax.persistence.*", "org.hibernate.annotations.*"*/})
public class BeanMetadataGenerator extends AbstractProcessor {
    public static final String PREFIX = "P";
    private static final String BASE_CLASS = PREFIX + Object.class.getSimpleName();
    private String intend = "    ";
    private static final String JAVA_LANG = "java.lang";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (roundEnv.processingOver() || annotations.size() == 0) {
            return false;
        }
        Set<? extends Element> elements = roundEnv.getRootElements();
        for (Element e : elements)
            if (asList(CLASS, INTERFACE).contains(e.getKind()))
                generate((TypeElement) e, elements, properties(e, elements));

        generateBaseClass();


        return false;
    }

    public void generate(TypeElement classElement, Set<? extends Element> elements, Map<String, TypeElement> properties) {
        Name className = classElement.getSimpleName();
        PackageElement packageElement = (PackageElement) classElement.getEnclosingElement();
        Name pkgName = packageElement.getQualifiedName();
        Filer processingEnvFiler = processingEnv.getFiler();
        String metadataClassName = PREFIX + className;
        BufferedWriter bw = null;
        try {
            processingEnv.getMessager().printMessage(WARNING,
                    "Generating " + pkgName + "." + metadataClassName);
            JavaFileObject jfo = processingEnvFiler.createSourceFile(pkgName + "." + metadataClassName);
            bw = new BufferedWriter(jfo.openWriter());
            bw.append("package ").append(pkgName).append(";");
            bw.newLine();

            for (TypeElement elem : properties.values())
                if (elem != null) {
                    bw.append("import " + qualifiedMetadataName(elem) + ";");
                    bw.newLine();
                }

            TypeMirror _tmp = classElement.getSuperclass();
            DeclaredType superClass = _tmp instanceof DeclaredType ? (DeclaredType) _tmp : null;
            if (superClass == null && !(_tmp instanceof NoType))
                throw new IllegalStateException("incompatible base type " + superClass + " for " + classElement);

            bw.newLine();

            bw.append("public class ").append(metadataClassName);
            bw.append(" extends ");
            if (superClass != null) {
                TypeElement superElem = (TypeElement) superClass.asElement();


                if (elements.contains(superElem)) bw.append(qualifiedMetadataName(superElem));
                else bw.append(JAVA_LANG + "." + BASE_CLASS);
            } else bw.append(JAVA_LANG + "." + BASE_CLASS);
            bw.append(" {");
            bw.newLine();
            bw.append(intend).append("public ").append(metadataClassName).append("(String parent) { super(parent); }");
            bw.newLine();
            bw.append(intend).append("public ").append(metadataClassName).append("() { super(); }");
            bw.newLine();


            bw.newLine();
            for (String result : properties.keySet()) {
                TypeElement elem = properties.get(result);
                String _prop;
                if (elem != null) {
                    String type = metadataName(elem);
                    _prop = type + " " + result + " = new " + type + "(\"" + result + "\");";
                } else _prop = "CharSequence " + result + " = \"" + result + "\";";
                //bw.append(intend).append("public final static " + _prop);
                bw.append(intend).append("public final " + _prop);
                bw.newLine();
            }

            bw.append(intend).append("public static final " + metadataClassName + " "
                    + onlyUps(className.toString()).toLowerCase()
                    + " = new " + metadataClassName + "();");
            ;
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
            bw.append(intend).append("private final String BASE_PREFIX;");
            bw.newLine();
            bw.append(intend).append("public ").append(metadataClassName).append("(String parent) { BASE_PREFIX = parent; }");
            bw.newLine();
            bw.append(intend).append("public ").append(metadataClassName).append("() { BASE_PREFIX = \"\"; }");
            bw.newLine();

            bw.append(intend).append("public int length() { return BASE_PREFIX.length(); }");
            bw.newLine();
            bw.append(intend).append("public char charAt(int index) { return BASE_PREFIX.charAt(index); }");
            bw.newLine();
            bw.append(intend).append("public CharSequence subSequence(int start, int end) { return BASE_PREFIX.subSequence(start, end); }");
            bw.newLine();

            bw.append(intend).append("public String toString() { return BASE_PREFIX; }");
            bw.newLine();

            bw.append(intend).append("public CharSequence c(CharSequence p) { return BASE_PREFIX != null ? BASE_PREFIX +\".\" + p: p; }");
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

    public String metadataName(TypeElement elem) {
        return PREFIX + elem.getSimpleName().toString();
    }

    public String qualifiedMetadataName(TypeElement elem) {
        return packageName(elem) + "." + metadataName(elem);
    }

    public String packageName(TypeElement elem) {
        String s = elem.getSimpleName().toString();
        String q = elem.getQualifiedName().toString();
        return q.substring(0, q.length() - s.length() - 1);
    }

    public static Map<String, TypeElement> properties(Element e, Set<? extends Element> elements) {
        Map<String, TypeElement> properties = new LinkedHashMap<String, TypeElement>();
        for (Element childE : e.getEnclosedElements()) {
            Set<Modifier> modifiers = childE.getModifiers();
            boolean isPublic = modifiers.contains(PUBLIC);
            boolean isStatic = modifiers.contains(STATIC);

            String mName = childE.getSimpleName().toString();
            if (!isStatic && isPublic && METHOD == childE.getKind()) {
                ExecutableElement exec = (ExecutableElement) childE;
                TypeMirror returnType = exec.getReturnType();
                TypeKind kind = returnType.getKind();
                String result = null;

                boolean noParams = exec.getParameters().isEmpty();
                boolean booleanIs = noParams && BOOLEAN == kind && returnType instanceof PrimitiveType
                        && mName.startsWith("is") && mName.length() > 2
                        && mName.substring(2, 3).equals(mName.substring(2, 3).toUpperCase());

                boolean getter = noParams && mName.startsWith("get") && mName.length() > 3
                        && mName.substring(3, 4).equals(mName.substring(3, 4).toUpperCase());

                if (booleanIs) result = decapitalize(mName.substring(2));
                else if (getter) result = decapitalize(mName.substring(3));

                if (result != null) {
                    TypeElement type = null;
                    if (getter && returnType instanceof DeclaredType) {
                        DeclaredType dType = (DeclaredType) returnType;
                        Element retElement = dType.asElement();
                        if (elements.contains(retElement)) type = (TypeElement) retElement;
                    }
                    properties.put(result, type);
                }
            }
        }
        return properties;
    }

    public static String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() > 1 && isUpperCase(name.charAt(1)) && isUpperCase(name.charAt(0))) return name;
        char chars[] = name.toCharArray();
        chars[0] = toLowerCase(chars[0]);
        return new String(chars);
    }
}

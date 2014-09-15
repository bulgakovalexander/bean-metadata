package ru.buls;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.BOOLEAN;

/**
 * Created by alexander on 14.09.14.
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes({"*"/*,"javax.persistence.*", "org.hibernate.annotations.*"*/})
public class BeanMetadataGenerator extends AbstractProcessor {
    public static final String PREFIX = "P";
    private String intend = "    ";

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
            if (e.getKind() == CLASS) {
                TypeElement classElement = (TypeElement) e;

                Set<String> properties = new LinkedHashSet<String>();
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

                        if (result != null) properties.add(result);

                    }
                }

                PackageElement packageElement =
                        (PackageElement) classElement.getEnclosingElement();
                Filer processingEnvFiler = processingEnv.getFiler();
                try {
                    if (!properties.isEmpty()) {
                        JavaFileObject jfo;
                        Name className = classElement.getSimpleName();
                        Name pkgName = packageElement.getQualifiedName();
                        String metadataClassName = PREFIX + className;
                        jfo = processingEnvFiler.createSourceFile(pkgName + "." + metadataClassName);
                        BufferedWriter bw = new BufferedWriter(jfo.openWriter());
                        bw.append("package ");
                        bw.append(pkgName);
                        bw.append(";");
                        bw.newLine();
                        bw.newLine();
                        bw.write("public class " + metadataClassName + " {");
                        bw.newLine();

                        for (String result : properties) {
                            bw.write(intend + "public final static CharSequence " + result + " = \"" + result + "\";");
                            bw.newLine();
                        }

                        bw.write("}");
                        bw.close();
                    }
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }


                // rest of generated class contents
            }
        return false;
    }

    public static String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() > 1 && isUpperCase(name.charAt(1)) && isUpperCase(name.charAt(0))) return name;
        char chars[] = name.toCharArray();
        chars[0] = toLowerCase(chars[0]);
        return new String(chars);
    }
}

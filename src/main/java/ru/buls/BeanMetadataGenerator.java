package ru.buls;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.JavaFileObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import static java.lang.Boolean.TRUE;
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
    public static final String INTERFACE_PREFIX = "IP";
    public static final String STATIC_PREFIX = "S";
    private static final String WRAP_METHOD = "w";
    private static final String _PREFIX = "_PREFIX";
    private static final String PARENT = "parent";
    private String intend = "    ";
    private static final String JAVA_LANG = "javax.metadata";

    String filter = null;
    private Collection<String> include;
    private Collection<String> exclude;
    private boolean checkSuperclass = false;
    private String prefix = PREFIX;
    private String staticPrefix = STATIC_PREFIX;
    private String interfacePrefix = INTERFACE_PREFIX;

    private Map<String, TypeElement> generated = new HashMap<String, TypeElement>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        Map<String, String> options = processingEnv.getOptions();
        this.filter = options.get("filter");

        include = getFilter("include", options);
        exclude = getFilter("exclude", options);

        checkSuperclass = TRUE.toString().equals(options.get("checkSuperclass"));

        prefix = options.get("prefix");
        if (prefix == null) prefix = PREFIX;
        staticPrefix = options.get("staticPrefix");
        if (staticPrefix == null) staticPrefix = STATIC_PREFIX;
        interfacePrefix = options.get("interfacePrefix");
        if (interfacePrefix == null) interfacePrefix = INTERFACE_PREFIX;
    }

    private String getStaticPrefix() {
        return staticPrefix;
    }

    private String getInterfacePrefix() {
        return interfacePrefix;
    }

    private String getPrefix() {
        return prefix;
    }

    private String getFullObjectClassName() {
        return JAVA_LANG + "." + getObjectClassName();
    }

    protected Collection<String> getFilter(String filter, Map<String, String> options) {
        String _tmp = options.get(filter);
        return (_tmp != null) ? Arrays.asList(_tmp.split(",")) : Collections.<String>emptyList();
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
        boolean include = isInclude(classElement);
        if (include) {
            generate(classElement, elements, properties, false);
            generate(classElement, elements, properties, true);
        }
    }

    public boolean isInclude(TypeElement classElement) {
        boolean result = isObjectClass(classElement);
        if (result) return result;

        for (String pkg : include)
            if (classElement.toString().startsWith(pkg)) {
                result = true;
                break;
            }

        if (result) {
            for (String pkg : exclude)
                if (classElement.toString().startsWith(pkg)) {
                    result = false;
                    break;
                }
            if (!result) warning("class " + classElement.toString() + " is excluded by " + this.exclude);
        } else warning("class " + classElement.toString() + " is filtered out by " + this.include);
        return result;
    }

    public void generate(TypeElement classElement, Set<? extends Element> elements,
                         Map<String, TypeElement> properties, boolean isStatic) {
        if (!isStatic /*&& isInterface*/) {
            generate(classElement, elements, properties, isStatic, true);
        }
        generate(classElement, elements, properties, isStatic, false);
    }

    protected static boolean isInterface(TypeElement classElement) {
        boolean isInterface = false;
        if (classElement instanceof Symbol.ClassSymbol) {
            Symbol.ClassSymbol cs = (Symbol.ClassSymbol) classElement;
            isInterface = cs.isInterface();
        }
        return isInterface;
    }

    protected void generate(TypeElement classElement, Set<? extends Element> elements,
                            Map<String, TypeElement> properties, boolean isStatic, boolean isInterface) {
        Name className = classElement.getSimpleName();
        PackageElement packageElement = (PackageElement) classElement.getEnclosingElement();
        Name pkgName = packageElement.getQualifiedName();
        Filer processingEnvFiler = processingEnv.getFiler();

        String prefix = isStatic ? getStaticPrefix() : isInterface ? getInterfacePrefix() : getPrefix();
        String metadataClassName = prefix + className;
        BufferedWriter body = null;
        try {
            String msg = "Generating " + pkgName + "." + metadataClassName;
//            warning(msg);

            StringWriter out = new StringWriter();
            body = new BufferedWriter(out);

            Set<String> imports = new LinkedHashSet<String>();

            imports.add(getFullObjectClassName());

            TypeMirror _tmp = classElement.getSuperclass();
            DeclaredType superClass = _tmp instanceof DeclaredType ? (DeclaredType) _tmp : null;
            if (superClass == null && !(_tmp instanceof NoType))
                throw new IllegalStateException("incompatible base type " + superClass + " for " + classElement);

            body.newLine();

            body.append("public ").append(isInterface ? "interface" : "class").append(" ").append(metadataClassName);

            boolean implementsListStarted = false;
            if (superClass != null) {

                TypeElement superElem = (TypeElement) superClass.asElement();

                boolean superInclude = isInclude(superElem);
                boolean superClassFound = !checkSuperclass || elements.contains(superElem);
                boolean extend = superInclude && superClassFound && !(isInterface && isObjectClass(superElem));
                if (extend) {
                    body.append(" extends ");
                    String name;
                    if (isObjectClass(superElem)) imports.add(JAVA_LANG + "." + (name = getObjectClassName()));
                    else
                        imports.add(packageName(superElem) + "." + (name = metadataName(superElem, isStatic, isInterface)));

                    body.append(name);

                    implementsListStarted = isInterface;
                } else if (!isInterface) {
                    body.append(" extends ");
                    body.append(getObjectClassName());
                    imports.add(JAVA_LANG + "." + getObjectClassName());
                }
            } else if (!isInterface) {
                body.append(" extends ");
                body.append(getObjectClassName());
                imports.add(JAVA_LANG + "." + getObjectClassName());
            }

            if (!isStatic) {
                if (!isInterface) {
                    String name = getInterfacePrefix() + className;
                    body.append("\n").append(intend).append(intend)
                            .append("implements ").append(name);
                    implementsListStarted = true;
                }

                List<? extends TypeMirror> interfaces = classElement.getInterfaces();
                List<Element> iElements = new ArrayList<Element>(interfaces.size());
                for (TypeMirror i : interfaces) {
                    Element e = get(i, elements);
                    if (e != null) iElements.add(e);
                }
                if (!iElements.isEmpty()) {
                    if (!implementsListStarted)
                        body.append("\n").append(intend).append(intend).append(isInterface ? "extends " : "implements ");
                    boolean first = !implementsListStarted;
                    for (Element i : iElements) {
                        Name iName = i.getSimpleName();
                        PackageElement iPgk = (PackageElement) i.getEnclosingElement();
                        Name iPgkName = iPgk.getQualifiedName();
                        if (!first) body.append(",\n").append(intend).append(intend);

                        String name = getInterfacePrefix() + iName.toString();
                        imports.add(iPgkName.toString() + "." + name);
                        body.append(name);
                        first = false;
                    }
                }
            }
            body.append(" {");
            body.newLine();
            if (!(isStatic || isInterface)) {
                body.append(intend).append("public ").append(metadataClassName)
                        .append("(String prefix, " + getObjectClassName() + " parent) { super(prefix, parent); }");
                body.newLine();
                body.append(intend).append("protected ").append(metadataClassName).append("() { super(); }");
                body.newLine();
            }

            body.newLine();
            for (String result : properties.keySet()) {
                TypeElement elem = properties.get(result);

                if (elem != null) {
                    String newObjType = metadataName(elem, false, false);
                    String returnObjType = metadataName(elem, false, isInterface(elem));
                    String packageName = packageName(elem);
                    imports.add(packageName + "." + newObjType);
                    imports.add(packageName + "." + returnObjType);
                    String _method = returnObjType + " " + result + "()";
                    body.append(intend);
                    if (!isInterface) {
                        _method += " { return " + " new " + newObjType + "(\"" + result + "\", "
                                + (isStatic ? "null" : "this") + "); }";
                        body.append("public ");
                        if (isStatic) body.append("static ");
                    }
                    body.append(_method);
                    if (isInterface) body.append(";");
                    body.newLine();
                }
                if (!isInterface) {
                    String _prop = "String " + result + " = " + w(result, !(isStatic || isInterface)) + ";";
                    body.append(intend).append("public final ");
                    if (isStatic) body.append("static ");
                    body.append(_prop);
                    body.newLine();
                }
            }

//            if (!isStatic) bw.append(intend).append("public static final ")
//                    .append(metadataClassName).append(" ").append(onlyUps(className.toString()).toLowerCase())
//                    .append(" = new ").append(metadataClassName).append("();");

            body.newLine();
            body.append("}");

            JavaFileObject jfo = processingEnvFiler.createSourceFile(pkgName + "." + metadataClassName);
            BufferedWriter result = new BufferedWriter(jfo.openWriter());

            result.append("package ").append(pkgName).append(";");
            result.newLine();

            if(!imports.isEmpty()) result.newLine();
            for (String imp : imports) {
                result.append("import ").append(imp).append(";");
                result.newLine();
            }
            if(!imports.isEmpty()) result.newLine();

            body.flush();
            result.append(out.getBuffer().toString());
            body.close();

            result.flush();
            generated.put(pkgName.toString() + "." + className.toString(), classElement);

        } catch (IOException e1) {
            throw new RuntimeException(e1);
        } finally {
            if (body != null) try {
                body.close();
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
        Symbol.TypeSymbol whatSymbol = toSymbol(what);

        if (where.contains(whatSymbol)) {
            result = whatSymbol;
        }
//        for (Element e : where) {
//            System.out.println(
//                    e.toString() + ", " + e.asType() + ", " + what);
//            if (e.equals(whatSymbol)) {
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

    private static Symbol.TypeSymbol toSymbol(TypeMirror what) {
        return ((Type.ClassType) what).asElement();
    }

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
        String metadataClassName = getObjectClassName();
        String qualifiedBaseClass = JAVA_LANG + "." + metadataClassName;
        Filer processingEnvFiler = processingEnv.getFiler();
        BufferedWriter bw = null;

        warning("Generating " + qualifiedBaseClass);

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

    public String metadataName(TypeElement elem, boolean isStatic, boolean isInterface) {
        return (isStatic ? getStaticPrefix() : isInterface ? getInterfacePrefix() : getPrefix()) + elem.getSimpleName().toString();
    }

    public String qualifiedMetadataName(TypeElement elem, boolean isStatic, boolean isInterface) {
        if (isObjectClass(elem)) return getFullObjectClassName();
        else return packageName(elem) + "." + metadataName(elem, isStatic, isInterface);
    }

    protected static boolean isObjectClass(TypeElement elem) {
        return isObjectClass(elem.asType());
    }

    protected static boolean isObjectClass(TypeMirror type) {
        return type.toString().equals(Object.class.getName());
    }

    public String packageName(TypeElement elem) {
        String s = elem.getSimpleName().toString();
        String q = elem.getQualifiedName().toString();
        return q.substring(0, q.length() - s.length() - 1);
    }

    public static Map<String, TypeElement> properties(TypeElement e, Set<? extends Element> elements) {

        Map<String, TypeElement> fromIfaces = new LinkedHashMap<String, TypeElement>();
        //if (isInterface(e)) {
        List<? extends TypeMirror> interfaces = e.getInterfaces();
        for (TypeMirror i : interfaces) {
            addFromType(i, fromIfaces, elements, false);
        }
        //}

        Map<String, TypeElement> fromSuperClass = new LinkedHashMap<String, TypeElement>();

        addFromType(e.getSuperclass(), fromSuperClass, elements, true);
        Map<String, TypeElement> fromSelf = new LinkedHashMap<String, TypeElement>();
        addFromType(e.asType(), fromSelf, elements, false);

        for (String property : fromSelf.keySet()) fromIfaces.remove(property);
        for (String property : fromSuperClass.keySet()) fromIfaces.remove(property);

        LinkedHashMap<String, TypeElement> result = new LinkedHashMap<String, TypeElement>();
        result.putAll(fromIfaces);
        result.putAll(fromSelf);
        return result;
    }


    static void addFromType(TypeMirror type, Map<String, TypeElement> properties, Set<? extends Element> elements, boolean superclass) {
        while (type instanceof DeclaredType) {
            DeclaredType sdt = (DeclaredType) type;
            Element element = sdt.asElement();
            if (element != null && element instanceof TypeElement
                    && !((TypeElement) element).getQualifiedName().toString().equals(Object.class.getName())) {
                populate(properties, (TypeElement) element, elements);
                type = superclass ? ((TypeElement) element).getSuperclass() : null;
            } else type = null;
        }
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

                if (isField) result = mName;
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

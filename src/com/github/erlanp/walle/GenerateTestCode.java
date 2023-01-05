/*
 * Copyright xusong
 */

package com.github.erlanp.walle;

import com.github.erlanp.utils.PsiClassUtils;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.Notifications;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiUtil;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class GenerateTestCode {
    /**
     * 输出测试文件类名
     */
    public String testClassName;

    /**
     * 是否使用 junit5
     */
    public Boolean junit5 = false;

    /**
     * java文件夹， 用于生成测试的 when代码 可以自定义 比如 D:/java/lok/target/test-classes/../../src/
     */
    private static String filePath = "";

    /**
     * 生成 单元测试 InjectMocks 的变量名称
     */
    private String serviceName = "";

    // 感觉 answer = Answers.RETURNS_DEEP_STUBS 用处不大，先关上
    private Boolean useAnswers = false;

    private Boolean isSuperclass = false;

    // 是否生成私有方法的单元测试
    private Boolean genPrivateMethod = false;

    // 测试文件在同一个包
    private Boolean samePackage = true;

    private String jsonFn = "";

    private String author = "";

    private String copyright = "";

    /**
     * 常用的 Exception
     */
    private Class importException = Exception.class;

    private String fileContent = "";

    private String importAny = "static org.mockito.ArgumentMatchers";

    // 用于不确定的泛型 比如 'com.areyoo.lok.service.api.WwService|T': String.class
    private Map<String, Class> genericMap = new HashMap<>(15);

    // 用于生成测试类继承的类
    private Class baseTest = null;

    private Set<String> importSet = new HashSet<>(16);

    private Map<String, String> defaultMap = new HashMap<>(16);

    private Map<String, String> newMap = new HashMap<>(16);

    private StringBuffer stringBuffer = new StringBuffer();

    public void run(PsiClass myClass) throws Exception {
        PsiFile file = myClass.getContainingFile();
        PsiDirectory dir = file.getContainingDirectory();
        String basePath = myClass.getProject().getBasePath().replace("\\", "/");

        String javaFileName = file.toString().substring("PsiJavaFile:".length());
        String dirPath = dir.toString().substring("PsiDirectory:".length()).replace("\\", "/");
        String path = dirPath.substring(basePath.length());

        int mainJavaPos = path.indexOf("/src/main/java/");
        List<String> arr = new ArrayList<>(10);
        if (mainJavaPos != -1) {
            arr.addAll(Arrays.asList("", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten"));
        }
        String testPath = path.replace("/src/main/java/", "/src/test/java/");

        File testFile = null;

        String testFilePath = null;

        for (String numString : arr) {
            testFilePath = basePath + testPath + "/" + javaFileName.substring(0,
                    javaFileName.length() - ".java".length()) + numString + "Test.java";
            testFile = new File(testFilePath);
            if (!testFile.exists()) {
                testClassName = javaFileName.substring(0, javaFileName.length() - ".java".length()) + numString;
                break;
            }
        }

        genCode(myClass, isSuperclass, true);
        genCode(myClass, isSuperclass, false);

        // 写入剪切板
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable tText = new StringSelection(stringBuffer.toString());
        clip.setContents(tText, null);

        // 发通知
        NotificationGroup notificationGroup = new NotificationGroup("walle generate UT plugin",
                NotificationDisplayType.BALLOON, true);

        Notification notification = notificationGroup.createNotification("Generate UT complate, Copied to clipboard",
                MessageType.INFO);
        Notifications.Bus.notify(notification);

        File testDir = new File(basePath + testPath);
        if (testClassName == null || !(testDir.exists() || testDir.mkdirs())) {
            // 如果没有mkdir权限或者不确定测试文件夹位置
            MessageDialogBuilder.yesNo("Generate UT complate", "Generate UT complate, Copied to clipboard").show();
        } else {
            BufferedWriter writer = new BufferedWriter(new FileWriter(testFilePath));
            writer.write(stringBuffer.toString());
            writer.close();
            MessageDialogBuilder.yesNo("Generate UT complate", "Generate UT complate, " + testFilePath).show();
        }
    }

    private Boolean isInit = true;

    private void genCode(PsiClass myClass, Boolean isSuperclass, Boolean init) throws Exception {
        // 生成测试代码
        if ("java.lang.Object".equals(myClass.getQualifiedName())) {
            return;
        }
        if (init) {
            importSet = new HashSet<>(16);
            defaultMap = new HashMap<>(16);
        }
        isInit = init;
        this.isSuperclass = isSuperclass;
        String name = getType(myClass.getQualifiedName());
        if ("".equals(serviceName)) {
            serviceName = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }
        String currPackage = myClass.getQualifiedName().substring(0,
                myClass.getQualifiedName().length() - myClass.getName().length() - 1);
        println("/*\n" +
                " * Copyright" + copyright + "\n" +
                " */\n");
        println("package " + currPackage + ";");
        println("");

        setImport("org.mockito.Mock");
        setImport("org.junit.Assert");
        if (junit5) {
            setImport("org.junit.jupiter.api.Test");
        } else {
            setImport("org.junit.Test");
        }
        setImport("static org.mockito.Mockito.when");
        List<String> importList = new ArrayList<>(importSet);
        Collections.sort(importList);
        for (String importStr : importList) {
            String[] arr = importStr.split("[.]");
            if (arr.length == 1 || (importStr.contains("java.lang") && arr.length == 3)) {
                continue;
            } else if (samePackage && importStr.equals(currPackage + "." + getType(importStr))) {
                continue;
            }
            println("import " + importStr + ";");
        }
        println("");

        Set<PsiField> fields = getDeclaredFields(myClass);
        List<String> lineList = readFileContent(myClass);

        fileContent = String.join("\n", lineList);
        Map<String, List<String>> map = new HashMap<>(16);

        println("/**\n" +
                " * " + name + " UT\n" +
                " *" + "\n" +
                " * @author " + author + "\n" +
                " * @date " + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "\n" +
                " */");


        if (baseTest == null) {
            println("public class " + (testClassName != null ? testClassName : name) + "Test {");
        } else {
            setImport(baseTest.getName());
            println("public class " + (testClassName != null ? testClassName : name) + "Test extends " + baseTest.getSimpleName() + " {");
        }

        setImport("org.mockito.InjectMocks");
        println("@InjectMocks");

        if (!myClass.hasModifier(JvmModifier.ABSTRACT)) {
            println("private " + myClass.getName() + " " + serviceName + ";");
        } else {
            println("private " + myClass.getName() + " " + serviceName + " = new " + myClass.getName() +
                    "() {};");
        }
        println("");
        int number = 0;

        List<String> valueList = new ArrayList<>();

        List<String> scalarList = Arrays.asList("short", "byte", "char", "long", "int", "double", "float", "boolean");
        for (PsiField psiField : fields) {
            PsiType psiType = psiField.getType();
            if (psiField.hasModifier(JvmModifier.FINAL) || psiField.hasModifier(JvmModifier.STATIC)
                    || scalarList.contains(psiType.getCanonicalText())) {
                // FINAL or STATIC or scalar
                continue;
            }

            PsiClass psiClass = PsiUtil.resolveClassInType(psiField.getType());
            if (psiField.getAnnotations().length > 0 && PsiClassUtils.isNotSystemClass(psiClass)) {
                setImport("static org.mockito.Mockito.mock");
                if (useAnswers) {
                    setImport("org.mockito.Answers");
                    println("@Mock(answer = Answers.RETURNS_DEEP_STUBS)");
                } else {
                    println("@Mock");
                }
                setImport(psiClass.getQualifiedName());
                println("private " + psiClass.getName() + " " + psiField.getName() + ";");
                println("");

                for (PsiMethod serviceMethod : getDeclaredMethods(psiClass, true)) {
                    String methodStr = psiField.getName() + "." + serviceMethod.getName() + "(";
                    PsiType t = serviceMethod.getReturnType();
                    if (t == null) {
                        continue;
                    }
                    if (!"void".equals(t.getCanonicalText()) && ("".equals(fileContent) || fileContent.indexOf(methodStr) > 0)) {
                        if (!map.containsKey(methodStr)) {
                            map.put(methodStr, new ArrayList<>(10));
                        }
                        String whenStr = getWhen(serviceMethod, number, psiField);
                        if (whenStr != null) {
                            map.get(methodStr).add(getWhen(serviceMethod, number, psiField));
                        }
                        number++;
                    } else if ("void".equals(t.getCanonicalText()) && ("".equals(fileContent) || fileContent.indexOf(methodStr) > 0)) {
                        if (!map.containsKey(methodStr)) {
                            map.put(methodStr, new ArrayList<>(10));
                        }
                        map.get(methodStr).add(getVoidWhen(serviceMethod, psiField));
                        number++;
                    }
                }
            } else if (!PsiClassUtils.isNotSystemClass(psiClass)) {
                // 如果有类型是java基类
                String setFieldStr = "if (ReflectionTestUtils.getField(" + serviceName + ", \"" + psiField.getName() +
                        "\") != null) {\n" + "            ReflectionTestUtils.setField(" + serviceName + ", \"" + psiField.getName() +
                        "\", " + getDefaultVal(psiClass.getQualifiedName()) + ");\n        }";
                setImport("org.springframework.test.util.ReflectionTestUtils");
                valueList.add(setFieldStr);
            }
        }

        if (baseTest == null) {
            setImport("org.mockito.MockitoAnnotations");
            if (junit5) {
                setImport("org.junit.jupiter.api.BeforeEach");
            } else {
                setImport("org.junit.Before");
            }
            println((junit5 ? "@BeforeEach" : "@Before") + "\n" +
                    "    public void before() {\n" +
                    "        MockitoAnnotations.openMocks(this);\n");
            if (valueList.size() > 0) {
                valueList.forEach((value) -> {
                    println("        " + value);
                });
            }
            println("    }");
        } else if (valueList.size() > 0) {
            // 生成反射给成员变量赋值的代码
            if (junit5) {
                setImport("org.junit.jupiter.api.BeforeEach");
                println("@BeforeEach");
            } else {
                setImport("org.junit.Before");
                println("@Before");
            }
            println("public void beforeInit() {");
            valueList.forEach((value) -> {
                println(value);
            });
            println("}");
            println("");
        }

        Map<String, Set<List<String>>> whenMap = new HashMap<>(16);

        // 函数之间的关系
        Map<String, Set<String>> whenMethod = new HashMap<>(16);
        Map<String, Map<String, String>> putString = new HashMap<>(16);
        Set<PsiMethod> methods = getDeclaredMethods(myClass, true);

        for (PsiMethod method : methods) {
            whenMap.put(method.getName(), new HashSet<>(15));
            whenMethod.put(method.getName(), new HashSet<>(15));
            putString.put(method.getName(), new HashMap<>(15));
        }
        String methodName = "";
        for (String line : lineList) {
            if (line.trim().length() <= 1) {
                continue;
            }

            boolean maybeFunction = (line.indexOf("(") != -1);
            if (maybeFunction && (line.indexOf("private") > 0 || line.indexOf("public") > 0 || line.indexOf(
                    "protected") > 0)) {
                for (PsiMethod method : methods) {
                    if (lineHasMethod(line, method.getName())) {
                        methodName = method.getName();
                    }
                }
            } else {
                if (maybeFunction) {
                    for (PsiMethod method : methods) {
                        if (!"".equals(methodName) && lineHasMethod(line, method.getName())) {
                            whenMethod.get(methodName).add(method.getName());
                        }
                    }
                    for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                        if (!"".equals(methodName) && line.indexOf(entry.getKey()) > 0) {
                            whenMap.get(methodName).add(entry.getValue());
                        }
                    }
                }
                addWord(putString, methodName, line);
            }
        }

        Map<String, Set<String>> tmpMethodMap = methodMap(whenMethod);
        setPutString(putString, tmpMethodMap);
        for (Map.Entry<String, Set<String>> entry : tmpMethodMap.entrySet()) {
            for (String key : entry.getValue()) {
                whenMap.get(entry.getKey()).addAll(whenMap.get(key));
            }
        }
        methods(myClass, whenMap, putString);
        println("}");
    }

    private String getVoidWhen(PsiMethod serviceMethod, PsiField field) throws Exception {
        String serviceName = field.getName();

        setImport("org.mockito.invocation.InvocationOnMock");
        setImport("static org.mockito.Mockito.doAnswer");
        String setLine = "doAnswer((InvocationOnMock invocation) -> {\n" +
                "            return null;\n" +
                "        })";
        return setLine + ".when(" + serviceName + ")." + methodParame(serviceMethod) + ";";
    }

    private void methods(PsiClass myClass, Map<String, Set<List<String>>> whenMap,
                         Map<String, Map<String, String>> putString) throws Exception {
        myClass.getAllMethodsAndTheirSubstitutors();
        List<Pair<PsiMethod, PsiSubstitutor>> allPairMethod = new ArrayList<>(getPairMethods(myClass));

        Map<String, Integer> methodCount = new HashMap<>();

        for (int k = 0; k < allPairMethod.size(); k++) {
            Pair<PsiMethod, PsiSubstitutor> pair = allPairMethod.get(k);
            PsiMethod method = pair.getFirst();
            if (method.hasModifier(JvmModifier.PRIVATE) && !genPrivateMethod) {
                continue;
            }

            PsiParameter[] parameter = method.getParameterList().getParameters();
            List<PsiType> psiTypes = new ArrayList<>(parameter.length);
            for (PsiParameter psiParameter : parameter) {
                psiTypes.add(psiParameter.getType());
            }

            List<String> meta = new ArrayList<>(psiTypes.size());
            List<String> metaType = new ArrayList<>(psiTypes.size());

            if (methodCount.get(method.getName()) == null) {
                methodCount.put(method.getName(), 0);
            } else {
                methodCount.put(method.getName(), methodCount.get(method.getName()) + 1);
            }

            if (k > 0) {
                println("");
            }
            println("/**\n" +
                    "     * " + method.getName() + "\n" +
                    "                    *\n" +
                    "     * @throws Exception\n" +
                    "                    */");
            println("@Test");
            println("public void " + method.getName() + getMethodCountName(methodCount.get(method.getName())) + "Test" +
                    "() throws Exception {");
            for (int i = 0; i < psiTypes.size(); i++) {
                // 取得每个参数的初始化
                if (isVo(PsiUtil.resolveClassInType(psiTypes.get(i)))) {
                    String setLine = psiTypes.get(i).getPresentableText() + " " + parameter[i].getName() + " = " +
                            getInitVo(psiTypes.get(i).getCanonicalText()) + ";";
                    println(setLine);
                } else {
                    String setLine = psiTypes.get(i).getPresentableText() + " " + parameter[i].getName() + " = " +
                            getDefaultVal(psiTypes.get(i).getCanonicalText()) + ";";
                    println(setLine);
                }
                meta.add(parameter[i].getName());
                metaType.add(getType(psiTypes.get(i).getCanonicalText()) + ".class");
            }
            if (method.getReturnType() == null) {
                continue;
            }
            String returnType = method.getReturnType().getCanonicalText();

            String defString = "";
            String assertString = "";
            if ("void".equals(returnType)) {
                println("String error = null;");
            } else if (returnType.indexOf(".") == -1) {
                defString = getType(returnType) + " result = ";
                assertString = "Assert.assertTrue(result == " + getDefaultVal(returnType) + ");";
            } else if (returnType.indexOf("java.util.List") == 0) {
                setImport("java.util.List");
                defString = "List result = ";
                assertString = "Assert.assertTrue(result != null && result.toString().indexOf(\"[\") == 0);";
            } else {
                if (method.hasModifier(JvmModifier.PRIVATE)) {
                    defString = "Object result = ";
                } else {
                    defString = getType(returnType) + " result = ";
                }
                assertString = "Assert.assertTrue(result != null);";
            }
            String joinStr = metaType.size() > 0 ? ", " : "";

            if (method.hasModifier(JvmModifier.PRIVATE)) {

                String superclassStr = isSuperclass ? ".getSuperclass()" : "";
                setImport("java.lang.reflect.Method");
                String initMethod = "Method method = " + serviceName + ".getClass()" + superclassStr +
                        ".getDeclaredMethod(\"" + method.getName() + "\"" + joinStr
                        + String.join(", ", metaType) + ");";
                println(initMethod);
                println("method.setAccessible(true);");
                println(defString + "method.invoke(" + serviceName + joinStr + String.join(", ", meta) + ");");
            } else {
                println(defString + invokeString(myClass, method, meta));
            }

            Set<List<String>> whenList = whenMap.get(method.getName());
            if (whenList != null && !whenList.isEmpty()) {
                for (List<String> oneList : whenList) {
                    println("");
                    println(String.join("\n", oneList));
                }

                if (method.hasModifier(JvmModifier.PRIVATE)) {
                    println("method.invoke(" + serviceName + joinStr + String.join(", ", meta) + ");");
                } else {
                    println(invokeString(myClass, method, meta));
                }
            }

            Boolean add = false;
            for (int i = 0; i < psiTypes.size(); i++) {
                PsiType paramType = psiTypes.get(i);
                if (paramType.getCanonicalText() == null) {
                    continue;
                }
                PsiClassReferenceType paramReferenceType = null;
                if (paramType instanceof PsiClassReferenceType) {
                    paramReferenceType = (PsiClassReferenceType) paramType;
                }
                String canonicalText = paramType.getCanonicalText();
                if (paramReferenceType != null && canonicalText.contains("<") && !canonicalText.contains("[") &&
                        (canonicalText.startsWith("java.util.List") || canonicalText.startsWith("java.util.Set"))) {
                    String defaultVal =
                            getDefaultVal(paramReferenceType.resolveGenerics().getSubstitutor().getSubstitutionMap().values().stream().findFirst().get());
                    if (!(defaultVal == null || "".equals(defaultVal))) {
                        println(parameter[i].getName() + ".add(" + defaultVal + ");");
                        add = true;
                    }
                } else if (paramReferenceType != null && canonicalText.contains("<") && !canonicalText.contains("[") &&
                        (canonicalText.startsWith("java.util.Map") || canonicalText.startsWith("java.util.HashMap"))) {

                    // 取得 Map<K, V> V 的类型
                    List<PsiType> substitutionList =
                            new ArrayList<>(paramReferenceType.resolveGenerics().getSubstitutor().getSubstitutionMap().values());

                    PsiType mapType = substitutionList.get(0);
                    PsiType mapKeyType = substitutionList.get(1);
                    String defaultVal = getDefaultVal(mapType);
                    String defaultKeyVal = getDefaultVal(mapKeyType);
                    if (!(defaultVal == null || "".equals(defaultVal)) && !(defaultKeyVal == null || "".equals(defaultKeyVal))) {
                        if ("java.lang.String".equals(mapKeyType.getCanonicalText()) && putString.containsKey(method.getName())) {
                            int finalI = i;
                            putString.get(method.getName()).forEach((key, value) -> {
                                if (mapKeyType.getCanonicalText() != null && mapKeyType.getCanonicalText().startsWith(value)) {
                                    println(parameter[finalI].getName() + ".put(\"" + key + "\", " + defaultVal + ");");
                                }
                            });
                        }
                        println(parameter[i].getName() + ".put(" + defaultKeyVal + ", " + defaultVal + ");");
                        add = true;
                    }
                }
            }

            if (add) {
                if (method.hasModifier(JvmModifier.PRIVATE)) {
                    println("method.invoke(" + serviceName + joinStr + String.join(", ", meta) + ");");
                } else {
                    println(invokeString(myClass, method, meta));
                }
            }

            if ("void".equals(returnType)) {
                setImport(importException.getName());
                println("try {");
                // 定义常用的 Exception
                println("} catch (" + importException.getSimpleName() + " exp) {");
                println("error = exp.getMessage();");
                println("}");
                println("Assert.assertTrue(error == null);");
            } else {
                println(assertString);
            }
            println("}");
        }

        for (Map.Entry<String, String> entry : defaultMap.entrySet()) {
            String localType = getType(entry.getKey());
            println("");

            String fnStr = "private " + localType + " " + getInitVo(entry.getKey())
                    + " throws Exception {\n"
                    + entry.getValue() + "\n}";

            println(fnStr);
        }
    }

    private String invokeString(PsiClass myClass, PsiMethod method, List<String> meta) {
        if (myClass.hasModifier(JvmModifier.STATIC) && myClass.hasModifier(JvmModifier.ABSTRACT)) {
            return myClass.getName() + "." + method.getName() + "(" + String.join(", ", meta) + ");";
        } else {
            return serviceName + "." + method.getName() + "(" + String.join(", ", meta) + ");";
        }
    }

    private String getMethodCountName(Integer count) {
        String[] arr = {"", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten"};
        if (count >= arr.length) {
            return "";
        } else {
            return arr[count];
        }
    }

    private List<String> getMethodParameterTypes(PsiMethod method) throws Exception {
        PsiParameter[] genericParameterTypes = method.getParameterList().getParameters();
        List<String> list = new ArrayList<>(genericParameterTypes.length);
        for (int i = 0; i < genericParameterTypes.length; i++) {
            PsiParameter genericType = genericParameterTypes[i];
            list.add(getDefType(getType(genericType.getName()), genericType));
        }
        return list;
    }

    private void setPutString(Map<String, Map<String, String>> putString, Map<String, Set<String>> whenMethod) {
        whenMethod.forEach((key, value) -> {
            if (value.isEmpty()) {
                return;
            }
            for (String method : value) {
                putString.get(key).putAll(putString.get(method));
            }
        });
    }

    // 复制函数之间的关系
    private Map<String, Set<String>> methodMap(Map<String, Set<String>> whenMethod) {
        Map<String, Set<String>> result = new HashMap<>(16);
        for (Map.Entry<String, Set<String>> entry : whenMethod.entrySet()) {
            result.put(entry.getKey(), methodSet(entry.getKey(), whenMethod, 23));
        }
        return result;
    }

    private Set<String> methodSet(String key, Map<String, Set<String>> whenMethod, Integer times) {
        Set<String> set = whenMethod.get(key);
        if (times.compareTo(0) <= 0 || set == null || set.isEmpty()) {
            return new HashSet<>(0);
        }
        Set<String> result = new HashSet<>(set);
        for (String key2 : set) {
            result.addAll(methodSet(key2, whenMethod, times - 1));
        }
        return result;
    }

    private void addWord(Map<String, Map<String, String>> putString, String methodName, String line) {
        int leftPos = line.indexOf('"');
        int rightPos = line.indexOf('"', leftPos + 2);
        if (!(line.indexOf("@") == -1 && leftPos != -1 && rightPos != -1)) {
            return;
        }
        String str = line.substring(leftPos + 1, rightPos);

        for (char c : str.toCharArray()) {
            if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.".indexOf(c) == -1) {
                return;
            }
        }
        if (putString.get(methodName) == null) {
            putString.put(methodName, new HashMap<>(16));
        }
        if (!line.contains("(")) {
            putString.get(methodName).put(str, "java.lang.String");
        } else {
            for (String className : importSet) {
                String typeName = getType(className);
                if (!className.contains("static ") && (line.contains("(" + typeName + ")")
                        || line.contains("(" + typeName + "<")
                        || line.contains(" " + typeName + ".valueOf(")
                        || line.contains(".get" + typeName + "("))) {
                    putString.get(methodName).put(str, className);
                    return;
                }
            }
            if (!putString.get(methodName).containsKey(str)) {
                putString.get(methodName).put(str, "java.lang.String");
            }
        }
    }

    private boolean lineHasMethod(String line, String methodName) {
        if (line.indexOf(methodName) > 0) {
            if (line.indexOf(" " + methodName + "(") >= 0) {
                return true;
            } else if (line.indexOf("this." + methodName + "(") >= 0) {
                return true;
            } else if (line.indexOf("this::" + methodName) >= 0) {
                return true;
            }
        }
        return false;
    }

    private String getWhen(PsiMethod serviceMethod, int number, PsiField psiField) throws Exception {
        String serviceName = psiField.getName();

        PsiType returnType = serviceMethod.getReturnType();

        String setLine = null;
        PsiClass returnClass = PsiUtil.resolveClassInType(returnType);
        if (isVo(returnClass)) {
            setLine = getPresentableText(returnType.getPresentableText()) + " then" + number + " = " +
                    getInitVo(returnClass.getQualifiedName()) + ";";
        } else if (returnType instanceof PsiClassReferenceType) {
            PsiClassReferenceType psiClassReferenceType = (PsiClassReferenceType) returnType;
            PsiClass psiClass = psiClassReferenceType.resolveGenerics().getElement();

            String returnTypeText = returnType.getCanonicalText();
            int same = 0;
            for (PsiParameter param : serviceMethod.getParameterList().getParameters()) {
                if (param.getType().getCanonicalText().equals(returnTypeText)) {
                    // 如果返回参数和入参匹配
                    return getDoReturnWhen(serviceMethod, psiField, same);
                }
                same++;
            }
            if (psiClass.getQualifiedName() != null && psiClass.getQualifiedName().startsWith("java.lang")) {
                setLine = getPresentableText(returnType.getPresentableText()) + " then" + number + " = " +
                        getDefaultVal(psiClass.getQualifiedName()) + ";";
            } else if (psiClass.getQualifiedName() != null && Arrays.asList("java.util.List", "java.util.Collection")
                    .contains(psiClass.getQualifiedName())) {
                setImport("java.util.List");
                setImport("java.util.ArrayList");
                if (returnTypeText.contains("<")) {
                    return getDoReturnListWhen(serviceMethod, psiField, psiClassReferenceType, number);
                }
                // 无法确定传参类型
                setLine = getPresentableText(returnType.getPresentableText()) + " then" + number + " = new " +
                        "ArrayList<>(10);";
            } else if (psiClass.getQualifiedName() != null && Arrays.asList("java.util.Map", "java.util.HashMap")
                    .contains(psiClass.getQualifiedName())) {
                setImport("java.util.Map");
                setImport("java.util.HashMap");
                if (returnTypeText.contains("<")) {
                    return getDoReturnMapWhen(serviceMethod, psiField, psiClassReferenceType, number);
                }
                // 无法确定传参类型
                setLine = getPresentableText(returnType.getPresentableText()) + " then" + number + " = new HashMap<>" +
                        "(15);";
            } else if (psiClass.getQualifiedName() != null) {
                setLine = getPresentableText(returnType.getPresentableText()) + " then" + number + " = " +
                        getDefaultVal(psiClass.getQualifiedName()) + ";";
            } else {
                // 无确定类型
                setLine = returnType.getPresentableText() + " then" + number + " = null;";
            }
        } else {
            return null;
        }
        if (getStrCount(fileContent, serviceName + "." + serviceMethod.getName() + "(") >= 2) {
            // 如果有多个相同的函数使用，则用 doAnswer
            setImport("org.mockito.invocation.InvocationOnMock");
            setImport("static org.mockito.Mockito.doAnswer");
            return setLine + "\ndoAnswer((InvocationOnMock invocation) -> {\n" +
                    "            return then" + number + ";\n" +
                    "        }).when(" + serviceName + ")." + methodParame(serviceMethod) + ";";
        }
        return setLine + "\nwhen(" + serviceName + "." + methodParame(serviceMethod) + ").thenReturn(then" + number + ");";
    }

    private String getPresentableText(String presentableText) {
        if (!presentableText.contains("<")) {
            return presentableText;
        }
        List<StringBuffer> list = new ArrayList<>(10);
        list.add(new StringBuffer());
        int curr = 0;
        for (char c : presentableText.toCharArray()) {
            if ("<> ,".indexOf(c) != -1) {
                list.add(new StringBuffer().append(c));
                list.add(new StringBuffer());
                curr += 2;
            } else {
                list.get(curr).append(c);
            }
        }
        StringBuffer result = new StringBuffer();
        for (StringBuffer buffer : list) {
            if (buffer.length() == 1 && "<> ,".indexOf(buffer.toString()) == -1) {
                // 如果是泛型
                return getType(presentableText);
            } else {
                result.append(buffer);
            }
        }
        return result.toString();
    }

    private String getDoReturnListWhen(PsiMethod serviceMethod, PsiField psiField,
                                       PsiClassReferenceType psiClassReferenceType, int number) throws Exception {
        String serviceName = psiField.getName();

        // 取得 List<E> E 的类型
        PsiType listType =
                psiClassReferenceType.resolveGenerics().getSubstitutor().getSubstitutionMap().values().stream().findFirst().get();

        String returnTypeText = listType.getCanonicalText();

        int same = 0;
        for (PsiParameter param : serviceMethod.getParameterList().getParameters()) {
            String canonicalText = param.getType().getCanonicalText();

            if (canonicalText.equals(returnTypeText)) {
                setImport("org.mockito.invocation.InvocationOnMock");
                setImport("static org.mockito.Mockito.doAnswer");
                String setLine = "doAnswer((InvocationOnMock invocation) -> {\n" +
                        "            List<Object> tmpList = new ArrayList<>(1);\n" +
                        "            tmpList.add(invocation.getArgument(" + same + "));\n" +
                        "            return tmpList;\n" +
                        "        })";
                return setLine + ".when(" + serviceName + ")." + methodParame(serviceMethod) + ";";
            }
            if (canonicalText.contains("<") && Arrays.asList(canonicalText.startsWith("java.util.Map"),
                    canonicalText.startsWith("java.util.HashMap"))
                    .contains(true) && param.getType() instanceof PsiClassReferenceType) {
                PsiClassReferenceType paramReferenceType = (PsiClassReferenceType) param.getType();
                if (returnTypeText.equals(paramReferenceType.resolveGenerics().getSubstitutor().getSubstitutionMap().values().stream().findFirst().get().getCanonicalText())) {
                    setImport("org.mockito.invocation.InvocationOnMock");
                    setImport("static org.mockito.Mockito.doAnswer");
                    setImport("java.util.Map");
                    String setLine = "doAnswer((InvocationOnMock invocation) -> {\n" +
                            "            List<Object> tmpList = new ArrayList<>(1);\n" +
                            "            if (!invocation.getArgument(0, Map.class).values().stream().findFirst()" +
                            ".isEmpty()) {\n" +
                            "                tmpList.add(invocation.getArgument(" + same + ", Map.class).values()" +
                            ".stream().findFirst().get());\n" +
                            "            }\n" +
                            "            return tmpList;\n" +
                            "        })";
                    return setLine + ".when(" + serviceName + ")." + methodParame(serviceMethod) + ";";
                }
            }
            same++;
        }
        String type = getPresentableText(serviceMethod.getReturnType().getPresentableText());
        String setLine = type + " then" + number + " = new ArrayList<>(10);";
        if (type.contains("<")) {
            setLine = setLine + "\nthen" + number + ".add(" + getDefaultVal(listType) + ");";
        }
        return setLine + "\nwhen(" + serviceName + "." + methodParame(serviceMethod) + ").thenReturn(then" + number + ");";
    }

    private String getDoReturnMapWhen(PsiMethod serviceMethod, PsiField psiField,
                                      PsiClassReferenceType psiClassReferenceType, int number) throws Exception {
        String serviceName = psiField.getName();

        // 取得 Map<K, V> V 的类型
        List<PsiType> substitutionList =
                new ArrayList<>(psiClassReferenceType.resolveGenerics().getSubstitutor().getSubstitutionMap().values());
        PsiType mapType = substitutionList.get(0);
        PsiType mapKeyType = substitutionList.get(1);

        String returnTypeText = mapType.getCanonicalText();

        int same = 0;
        for (PsiParameter param : serviceMethod.getParameterList().getParameters()) {
            String canonicalText = param.getType().getCanonicalText();

            if (canonicalText.equals(returnTypeText)) {
                setImport("org.mockito.invocation.InvocationOnMock");
                setImport("static org.mockito.Mockito.doAnswer");
                String setLine = "doAnswer((InvocationOnMock invocation) -> {\n" +
                        "            Map tmpMap = new HashMap<>(1);\n" +
                        "            tmpMap.put(" + getDefaultVal(mapKeyType) + ", invocation.getArgument(" + same +
                        "));\n" +
                        "            return tmpMap;\n" +
                        "        })";
                return setLine + ".when(" + serviceName + ")." + methodParame(serviceMethod) + ";";
            }
            same++;
        }
        String type = getPresentableText(serviceMethod.getReturnType().getPresentableText());
        String setLine = type + " then" + number + " = new HashMap<>(15);";
        if (type.contains("<")) {
            setLine =
                    setLine + "\nthen" + number + ".put(" + getDefaultVal(mapKeyType) + ", " + getDefaultVal(mapType) + ");";
        }
        return setLine + "\nwhen(" + serviceName + "." + methodParame(serviceMethod) + ").thenReturn(then" + number + ");";
    }

    private String getDoReturnWhen(PsiMethod serviceMethod, PsiField field, Integer same) throws Exception {
        String serviceName = field.getName();
        // 生成 when thenReturn 代码

        setImport("org.mockito.invocation.InvocationOnMock");
        setImport("static org.mockito.Mockito.doAnswer");
        String setLine = "doAnswer((InvocationOnMock invocation) -> {\n" +
                "            return invocation.getArgument(" + same + ");\n" +
                "        })";
        return setLine + ".when(" + serviceName + ")." + methodParame(serviceMethod) + ";";
    }

    private String methodParame(PsiMethod serviceMethod) {
        // 生成 when thenReturn 代码
        List<String> list = new ArrayList<>(10);
        for (PsiParameter param : serviceMethod.getParameterList().getParameters()) {
            list.add(getAny(param.getType()));
        }
        return serviceMethod.getName() + "(" + String.join(", ", list) + ")";
    }

    private String getAny(PsiType aClass) {
        String name = getType(aClass.getCanonicalText());
        char first = name.toCharArray()[0];

        if ("Object".equals(name) || (!name.contains(".") && name.length() == 1)) {
            setImport(importAny + ".any");
            return "any()";
        } else if (65 <= first && first <= 90) {
            // 大写头说明是类
            setImport(importAny + ".nullable");
            setImport(aClass.getCanonicalText());
            return "nullable(" + name + ".class)";
        } else {
            setImport(importAny + ".any");
            return "any(" + name + ".class)";
        }
    }

    public static int getStrCount(String source, String sub) {
        int count = 0;

        int fromIndex = 0;
        while (true) {
            fromIndex = source.indexOf(sub, fromIndex + 1);
            if (fromIndex != -1) {
                count++;
                if (count >= 2) {
                    break;
                }
            } else {
                break;
            }
        }

        return count;
    }

    private String getDefType(PsiType returnType, PsiField field) throws Exception {
        return getDefType(getType(returnType.getCanonicalText()), field);
    }

    private String getDefType(String returnTypeName, PsiParameter field) throws Exception {
        return returnTypeName;
    }

    private String getDefType(String returnTypeName, PsiField field) throws Exception {

        return returnTypeName;
    }

    private List<PsiMethod> getMethods(PsiClass myClass, Class myClass2) {
        List<String> resultList = getMethods(myClass2);
        List<PsiMethod> list = new ArrayList<>(10);

        List<PsiMethod> tmp = Arrays.asList(myClass.getAllMethods());
        Collections.shuffle(tmp);
        for (PsiMethod method : tmp) {
            if (!resultList.contains(method.getName()) && method.hasModifier(JvmModifier.PUBLIC)) {
                list.add(method);
            }
        }
        return list;
    }

    private List<String> getMethods(Class myClass2) {
        List<String> resultList =
                Arrays.asList(myClass2.getMethods()).stream().map(Method::getName).collect(Collectors.toList());

        resultList.add("Object");
        resultList.add("finalize");
        resultList.add("clone");
        return resultList;
    }

    private List<PsiMethod> getMethods(PsiClass myClass) {
        return getMethods(myClass, Object.class);
    }

    private Boolean isVo(PsiClass myClass) {
        if (myClass == null) {
            return false;
        }
        if (myClass.isInterface()) {
            if (myClass.getQualifiedName() != null && !myClass.getQualifiedName().startsWith("java.util")) {
                getDefaultVal(myClass);
            }
            return false;
        }
        if (myClass.getQualifiedName() == null || myClass.getQualifiedName().startsWith("java.lang")) {
            return false;
        }

        String defaultValue = getDefaultValue(myClass.getQualifiedName());
        if (!"null".equals(defaultValue)) {
            return false;
        }

        if (defaultMap.containsKey(myClass.getQualifiedName())) {
            return true;
        }

        List<PsiMethod> listMethod = getMethods(myClass);
        for (PsiMethod method : listMethod) {
            JvmParameter[] parameter = method.getParameters();
            if (method.getName().length() > 3 && "set".equals(method.getName().substring(0, 3)) && parameter.length == 1
                    && !getAnnotations(method.getAnnotations()).contains("java.lang.Deprecated")) {
                defaultMap.put(myClass.getQualifiedName(), getVo(myClass));
                return true;
            }
        }
        List<PsiField> lombokFieldList = getLombokField(myClass);
        if (!lombokFieldList.isEmpty()) {
            defaultMap.put(myClass.getQualifiedName(), getVo(myClass, lombokFieldList));
            return true;
        }
        for (PsiMethod method : listMethod) {
            JvmParameter[] parameter = method.getParameters();
            if (method.hasModifier(JvmModifier.STATIC)
                    && myClass.getQualifiedName().equals(method.getReturnType().getCanonicalText())
                    && !getAnnotations(method.getAnnotations()).contains("java.lang.Deprecated")) {
                if (method.hasModifier(JvmModifier.STATIC) && parameter.length == 0) {
                    defaultMap.put(myClass.getQualifiedName(), "return " + myClass.getName() + "." + method.getName() +
                            "();");
                    return true;
                }
            }
        }
        for (PsiMethod method : listMethod) {
            PsiParameter[] parameter = method.getParameterList().getParameters();
            if (method.hasModifier(JvmModifier.STATIC)
                    && myClass.getQualifiedName().equals(method.getReturnType().getCanonicalText())
                    && !getAnnotations(method.getAnnotations()).contains("java.lang.Deprecated")) {
                if (parameter.length == 1) {
                    defaultMap.put(myClass.getQualifiedName(), "return " + myClass.getName() + "." + method.getName() +
                            "(" + getDefaultVal(parameter[0]) + ");");
                    return true;
                }
            }
        }

        if (myClass.getName().indexOf("[") == -1 && !zeroConstructor(myClass) && !myClass.hasModifier(JvmModifier.FINAL)) {
            setImport(myClass.getQualifiedName());

            List<String> mockVoWhenList = new ArrayList<>(10);
            for (PsiMethod method : listMethod) {
                JvmParameter[] parameter = method.getParameters();
                if (parameter.length == 0 && !"void".equals(method.getReturnType().getCanonicalText()) && ((method.getName().length() >= 4
                        && "get".equals(method.getName().substring(0, 3))
                        && fileContent.contains(method.getName().substring(4))) ||
                        fileContent.contains(method.getName()))) {
                    mockVoWhenList.add("when(vo." + method.getName() + "()).thenReturn(" + getDefaultVal(method.getReturnType()) + ");");
                }
            }
            if (mockVoWhenList.isEmpty()) {
                defaultMap.put(myClass.getQualifiedName(), "return " + getMock(myClass) + ";");
            } else {
                mockVoWhenList.add("return vo;");
                defaultMap.put(myClass.getQualifiedName(), myClass.getName() +
                        " vo = " + getMock(myClass) + ";\n" +
                        String.join("\n", mockVoWhenList));
            }
            return true;
        }
        return false;
    }

    private List<PsiField> getLombokField(PsiClass myClass) {
        Boolean isLombok = hasLombok(myClass);
        PsiField[] fields = myClass.getAllFields();
        List<PsiField> fieldList = new ArrayList<>(fields.length);

        List<String> lombokList = Arrays.asList("lombok.Setter", "lombok.Data");
        for (PsiField field : fields) {
            if (isLombok) {
                fieldList.add(field);
                continue;
            }
            Boolean fieldIsLombok = false;
            for (String annotation : getAnnotations(field.getAnnotations())) {
                if (lombokList.contains(annotation)) {
                    fieldIsLombok = true;
                }
            }
            if (fieldIsLombok) {
                fieldList.add(field);
            }
        }
        return fieldList;
    }

    private List<String> getAnnotations(PsiAnnotation[] annotations) {
        return Arrays.stream(annotations).map(PsiAnnotation::getQualifiedName)
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    private Boolean hasLombok(PsiClass myClass) {
        List<String> lombokList = Arrays.asList("lombok.Setter", "lombok.Data");
        while (myClass != null) {
            for (PsiAnnotation annotation : myClass.getAnnotations()) {
                if (annotation.getQualifiedName() != null && lombokList.contains(annotation.getQualifiedName())) {
                    return true;
                }
            }
            myClass = myClass.getSuperClass();
        }
        return false;
    }

    private Boolean zeroConstructor(PsiClass aClass) {
        for (PsiMethod item : aClass.getConstructors()) {
            if (item.getParameters().length == 0) {
                return true;
            }
        }
        return false;
    }

    private String getVo(PsiClass myClass, List<PsiField> lombokFieldList) {
        List<String> result = new ArrayList<>(10);
        String localType = getType(myClass.getQualifiedName());
        result.add(localType + " vo = new " + localType + "();\n");
        for (PsiField field : lombokFieldList) {
            String name = field.getName().substring(0, 1).toUpperCase(Locale.ENGLISH) + field.getName().substring(1);
            if (fileContent.contains(name + "(")) {
                result.add("vo.set" + name + "(" + getDefaultVal(field.getType()) + ");\n");
            }
        }

        if (result.size() > 1) {
            return String.join("", result) + "return vo;";
        }
        // 如果没有匹配，就选第一个赋值
        for (PsiField field : lombokFieldList) {
            String name = field.getName().substring(0, 1).toUpperCase(Locale.ENGLISH) + field.getName().substring(1);
            result.add("vo.set" + name + "(" + getDefaultVal(field.getType()) + ");\n");
        }
        return String.join("", result) + "return vo;";
    }

    private String getVo(PsiClass myClass) {
        List<String> result = new ArrayList<>(10);
        String localType = getType(myClass.getQualifiedName());
        result.add(localType + " vo = new " + localType + "();\n");
        for (PsiMethod method : getMethods(myClass)) {
            PsiParameter[] parameter = method.getParameterList().getParameters();

            if (!method.hasModifier(JvmModifier.STATIC) && method.getName().length() >= 4
                    && "set".equals(method.getName().substring(0, 3)) && parameter.length == 1
                    && !getAnnotations(method.getAnnotations()).contains("java.lang.Deprecated")
                    && fileContent.contains(method.getName().substring(4) + "(")) {
                result.add("vo." + method.getName() + "(" + getDefaultVal(parameter[0]) + ");\n");
            }
        }
        if (result.size() > 1) {
            return String.join("", result) + "return vo;";
        }
        // 如果没有匹配，就选第一个赋值
        for (PsiMethod method : getMethods(myClass)) {
            PsiParameter[] parameter = method.getParameterList().getParameters();
            if (!method.hasModifier(JvmModifier.STATIC) && method.getName().length() >= 4
                    && "set".equals(method.getName().substring(0, 3)) && parameter.length == 1
                    && !getAnnotations(method.getAnnotations()).contains("java.lang.Deprecated")) {
                result.add("vo." + method.getName() + "(" + getDefaultVal(parameter[0]) + ");\n");
                break;
            }
        }
        if (result.size() > 1) {
            return String.join("", result) + "return vo;";
        }
        // 如果没有匹配，就选一个参数为空的函数
        for (PsiMethod method : getMethods(myClass)) {
            JvmParameter[] parameter = method.getParameters();
            if (!method.hasModifier(JvmModifier.STATIC) && parameter.length == 0
                    && !getAnnotations(method.getAnnotations()).contains("java.lang.Deprecated")) {
                result.add("vo." + method.getName() + "();\n");
                break;
            }
        }
        return String.join("", result) + "return vo;";
    }

    private String getDefaultVal(PsiType parameter) {
        String name = parameter.getCanonicalText();

        String result = getDefaultVal(name);
        if (result != null) {
            setImport(name);
            return result;
        }
        return getDefaultVal(PsiUtil.resolveClassInType(parameter));
    }

    private String getDefaultVal(PsiParameter parameter) {
        return getDefaultVal(parameter.getType());
    }

    private String getDefaultVal(PsiClass cls) {
        String result = null;
        if (cls != null) {
            String className = cls.getQualifiedName();
            setImport(className);
            if (defaultMap.get(cls) != null) {
                result = getInitVo(className);
            } else if (cls.isInterface()) {
                result = getMock(cls);
                newMap.put(className, result);
            } else {
                result = "new " + getType(className) + "()";
                newMap.put(className, result);
            }
        }
        return result;
    }

    private String getDefaultVal(String name) {
        int indexArr = name.indexOf("[");
        if (indexArr > 0) {
            name = name.substring(0, indexArr);
        }
        int index = name.indexOf("<");
        if (index > 0) {
            name = name.substring(0, index);
        }
        setImport(name);
        String result = null;
        if (defaultMap.get(name) != null) {
            result = getInitVo(name);
        } else if (newMap.containsKey(name)) {
            result = newMap.get(name);
        }
        if (result != null) {
            return result;
        }
        switch (name) {
            case "java.math.BigDecimal":
                result = "BigDecimal.ONE";
                break;
            case "java.math.BigInteger":
                result = "BigInteger.ONE";
                break;
            case "short":
            case "java.lang.Short":
                result = "(short)0";
                break;
            case "byte":
            case "java.lang.Byte":
                result = "(byte)1";
                break;
            case "char":
            case "java.lang.Character":
                result = "'1'";
                break;
            case "long":
            case "java.lang.Long":
                result = "1L";
                break;
            case "int":
            case "java.lang.Integer":
                result = "1";
                break;
            case "double":
            case "java.lang.Double":
                result = "1.0D";
                break;
            case "float":
            case "java.lang.Float":
                result = "1.0F";
                break;
            case "java.lang.String":
                result = "\"1\"";
                break;
            case "boolean":
            case "java.lang.Boolean":
                result = "true";
                break;
            case "java.util.List":
            case "java.util.Collection":
                setImport("java.util.ArrayList");
                result = "new ArrayList<>(10)";
                break;
            case "java.util.Map":
                setImport("java.util.HashMap");
                result = "new HashMap<>(16)";
                break;
            case "java.util.Set":
                setImport("java.util.HashSet");
                result = "new HashSet<>(16)";
                break;
        }
        if (indexArr > 0) {
            switch (name) {
                case "java.util.List":
                    result = "new ArrayList[1]";
                    break;
                case "java.util.Map":
                    result = "new HashMap[1]";
                    break;
                case "java.util.Set":
                    result = "new HashSet[1]";
                    break;
                default:
                    result = "new " + getType(name) + "[] {" + result + "}";
            }
            return result;
        }
        return result;
    }

    private String getMock(PsiClass myClass) {
        setImport("static org.mockito.Mockito.mock");
        setImport(myClass.getName());
        if (useAnswers) {
            setImport("org.mockito.Mockito");
            return "mock(" + myClass.getName() + ".class, Mockito.RETURNS_DEEP_STUBS)";
        } else {
            return "mock(" + myClass.getName() + ".class)";
        }
    }

    private String getInitVo(String className) {
        return "get" + getType(className).replace(".", "$") + "()";
    }

    private String getDefaultValue(String name) {
        String result;
        switch (name) {
            case "java.math.BigDecimal":
            case "java.math.BigInteger":
            case "long":
            case "java.lang.Long":
            case "int":
            case "java.lang.Integer":
            case "short":
            case "java.lang.Short":
            case "double":
            case "java.lang.Double":
            case "float":
            case "java.lang.Float":
            case "byte":
            case "java.lang.Byte":
            case "char":
            case "java.lang.Character":
            case "java.lang.String":
                result = "'1'";
                break;
            case "java.lang.Boolean":
            case "boolean":
                result = "true";
                break;
            case "java.util.List":
            case "java.util.Collection":
            case "java.util.Set":
                result = "[]";
                break;
            case "java.util.Map":
                result = "{}";
                break;
            case "java.util.Date":
                // gson 需要自定义 SimpleDateFormat result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                result = "null";
                break;
            default:
                result = "null";
        }
        return result;
    }

    private Set<Pair<PsiMethod, PsiSubstitutor>> getPairMethods(PsiClass myClass) {
        Set<Pair<PsiMethod, PsiSubstitutor>> set = new HashSet<>(15);
        List<String> objectMethods = getMethods(Object.class);
        for (Pair<PsiMethod, PsiSubstitutor> method : myClass.getAllMethodsAndTheirSubstitutors()) {
            if (!objectMethods.contains(method.getFirst().getName())) {
                set.add(method);
            }
        }
        if (myClass.getSuperClass() != null && myClass.getSuperClass().getQualifiedName() != null
                && !myClass.getSuperClass().getQualifiedName().contains("java.")) {
            for (Pair<PsiMethod, PsiSubstitutor> method : getPairMethods(myClass.getSuperClass())) {
                if (!objectMethods.contains(method.getFirst().getName())) {
                    set.add(method);
                }
            }
        }
        return set;
    }

    private Set<PsiMethod> getDeclaredMethods(PsiClass myClass, Boolean base) {
        PsiMethod[] methods = myClass.getAllMethods();
        Set<PsiMethod> set = new HashSet<>(15);
        for (PsiMethod method : methods) {
            if (base || method.hasModifierProperty(PsiModifier.PUBLIC)) {
                set.add(method);
            }
        }

        if (myClass.getSuperClass() != null && myClass.getSuperClass().getQualifiedName() != null
                && !myClass.getSuperClass().getQualifiedName().contains("java.")) {
            set.addAll(getDeclaredMethods(myClass.getSuperClass(), false));
        } else if (myClass.getInterfaces() != null && myClass.getInterfaces().length > 0) {
            for (PsiClass interFace : myClass.getInterfaces()) {
                set.addAll(getDeclaredMethods(myClass.getInterfaces()[0], false));
            }
        }

        return set;
    }

    private static List<String> readFileContent(PsiClass myClass) {
        List<String> sbf = new ArrayList<>(10);
        while (myClass.getSuperClass() != null && !"java.lang.Object".equals(myClass.getSuperClass().getQualifiedName())) {
            sbf.addAll(Arrays.asList(myClass.getText().split("\n")));
            myClass = myClass.getSuperClass();
        }
        return sbf;
    }

    private Set<PsiField> getDeclaredFields(PsiClass myClass) {
        Map<String, PsiField> fieldMap = new HashMap<>(15);
        for (PsiField field : myClass.getAllFields()) {
            fieldMap.put(field.getName(), field);
        }
        if (myClass.getSuperClass() != null && !myClass.getSuperClass().getQualifiedName().contains("java.")) {
            for (PsiField field : getDeclaredFields(myClass.getSuperClass())) {
                fieldMap.put(field.getName(), field);
            }
        }
        return new HashSet<>(fieldMap.values());
    }

    private void println(String item) {
        if (!isInit) {
            stringBuffer.append(item);
            stringBuffer.append("\n");
        }
    }

    private String getType(String type) {
        if (type.indexOf(";") != -1) {
            type = type.substring(2, type.indexOf(";"));
        }
        int index = type.indexOf("<");

        int indexArray = type.indexOf("[");
        String suffix = (indexArray > 0 && index > 0) ? type.substring(indexArray) : "";
        if (index > 0) {
            type = type.substring(0, index);
        }
        setImport(type);
        String[] arr = type.split("[.]");
        if (arr.length > 0) {
            if (type.indexOf("$") >= 0) {
                arr[arr.length - 1] = arr[arr.length - 1].replace("$", ".");
            }
            return arr[arr.length - 1] + suffix;
        } else {
            return "Object";
        }
    }

    private void setImport(String name) {
        if (!isInit || importSet.contains(name)) {
            return;
        }
        List<StringBuffer> list = new ArrayList<>(10);
        list.add(new StringBuffer());
        int current = 0;
        for (char item : name.toCharArray()) {
            if ("$[]<>,;".indexOf(item) != -1) {
                current++;
                list.add(new StringBuffer());
            } else {
                list.get(current).append(item);
            }
        }
        list.forEach((item) -> {
            String className = item.toString().trim();
            if (!"".equals(className)) {
                importSet.add(item.toString().trim());
            }
        });
    }
}
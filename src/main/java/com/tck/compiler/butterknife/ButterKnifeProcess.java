package com.tck.compiler.butterknife;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import com.tck.libannotation.butterknife.BindView;
import com.tck.libannotation.butterknife.OnClick;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.util.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

// 用来生成 META-INF/services/javax.annotation.processing.Processor 文件
@AutoService(Processor.class)
// 允许/支持的注解类型，让注解处理器处理
@SupportedAnnotationTypes({Constants.BINDVIEW_ANNOTATION_TYPES, Constants.ONCLICK_ANNOTATION_TYPES})
// 指定JDK编译版本
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ButterKnifeProcess extends AbstractProcessor {

    // 操作Element工具类 (类、函数、属性都是Element)
    private Elements elementUtils;

    // type(类信息)工具类，包含用于操作TypeMirror的工具方法
    private Types typeUtils;

    // Messager用来报告错误，警告和其他提示信息
    private Messager messager;

    // 文件生成器 类/资源，Filter用来创建新的类文件，class文件以及辅助文件
    private Filer filer;

    // key:类节点, value:被@BindView注解的属性集合
    private Map<TypeElement, List<VariableElement>> tempBindViewMap = new HashMap<>();

    // key:类节点, value:被@OnClick注解的方法集合
    private Map<TypeElement, List<ExecutableElement>> tempOnClickMap = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        messager.printMessage(Diagnostic.Kind.NOTE, "注解处理器初始化完成，开始处理注解------------------------------->");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (CollectionUtils.isNotEmpty(annotations)) {
            Set<? extends Element> bindViewElements = roundEnv.getElementsAnnotatedWith(BindView.class);
            Set<? extends Element> onClickElements = roundEnv.getElementsAnnotatedWith(OnClick.class);
            setBindViewMap(bindViewElements);
            setOnClickMap(onClickElements);
            try {
                createJavaFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }


        return false;
    }

    private void createJavaFile() throws IOException {
        if (tempBindViewMap.size() > 0) {
            // 获取ViewBinder接口类型（生成类文件需要实现的接口）
            TypeElement viewBinderType = elementUtils.getTypeElement(Constants.VIEWBINDER);
            TypeElement clickListenerType = elementUtils.getTypeElement(Constants.CLICKLISTENER);
            TypeElement viewType = elementUtils.getTypeElement(Constants.VIEW);

            for (Map.Entry<TypeElement, List<VariableElement>> entry : tempBindViewMap.entrySet()) {
                // 类名
                ClassName className = ClassName.get(entry.getKey());
                // 实现接口泛型
                ParameterizedTypeName typeName = ParameterizedTypeName.get(ClassName.get(viewBinderType),
                        ClassName.get(entry.getKey()));
                // 参数体配置(MainActivity target)
                ParameterSpec parameterSpec = ParameterSpec.builder(ClassName.get(entry.getKey()), // MainActivity
                        Constants.TARGET_PARAMETER_NAME) // target
                        .addModifiers(Modifier.FINAL)
                        .build();

                // 方法配置：public void bind(MainActivity target) {
                MethodSpec.Builder methodBuidler = MethodSpec.methodBuilder(Constants.BIND_METHOD_NAME) // 方法名
                        .addAnnotation(Override.class) // 重写注解
                        .addModifiers(Modifier.PUBLIC) // public修饰符
                        .addParameter(parameterSpec); // 方法参数

                for (Element fieldElement : entry.getValue()) {
                    // 获取属性名
                    String fieldName = fieldElement.getSimpleName().toString();
                    // 获取@BindView注解的值
                    int annotationValue = fieldElement.getAnnotation(BindView.class).value();
                    // target.tv = target.findViewById(R.id.tv);
                    String methodContent = "$N." + fieldName + " = $N.findViewById($L)";
                    methodBuidler.addStatement(methodContent,
                            Constants.TARGET_PARAMETER_NAME,
                            Constants.TARGET_PARAMETER_NAME,
                            annotationValue);
                }

                if (tempOnClickMap.size() > 0) {
                    for (Map.Entry<TypeElement, List<ExecutableElement>> methodEntry : tempOnClickMap.entrySet()) {
                        // 类名
                        if (className.equals(ClassName.get(entry.getKey()))) {
                            for (ExecutableElement methodElement : methodEntry.getValue()) {
                                // 获取方法名
                                String methodName = methodElement.getSimpleName().toString();
                                // 获取@OnClick注解的值
                                int annotationValue = methodElement.getAnnotation(OnClick.class).value();
                                /**
                                 * target.findViewById(2131165312).setOnClickListener(new DebouncingOnClickListener() {
                                 *      public void doClick(View view) {
                                 *          target.click(view);
                                 *      }
                                 * });
                                 */
                                methodBuidler.beginControlFlow("$N.findViewById($L).setOnClickListener(new $T()",
                                        Constants.TARGET_PARAMETER_NAME, annotationValue, ClassName.get(clickListenerType))
                                        .beginControlFlow("public void doClick($T view)", ClassName.get(viewType))
                                        .addStatement("$N." + methodName + "(view)", Constants.TARGET_PARAMETER_NAME)
                                        .endControlFlow()
                                        .endControlFlow(")")
                                        .build();
                            }
                        }
                    }
                }

                // 必须是同包（属性修饰符缺省），MainActivity$$ViewBinder
                JavaFile.builder(className.packageName(), // 包名
                        TypeSpec.classBuilder(className.simpleName() + "$ViewBinder") // 类名
                                .addSuperinterface(typeName) // 实现ViewBinder接口
                                .addModifiers(Modifier.PUBLIC) // public修饰符
                                .addMethod(methodBuidler.build()) // 方法的构建（方法参数 + 方法体）
                                .build()) // 类构建完成
                        .build() // JavaFile构建完成
                        .writeTo(filer); // 文件生成器开始生成类文件
            }
        }
    }

    private void setBindViewMap(Set<? extends Element> bindViewElements) {
        if (CollectionUtils.isNotEmpty(bindViewElements)) {
            for (Element bindViewElement : bindViewElements) {
                messager.printMessage(Diagnostic.Kind.NOTE, "@BindView >>> " + bindViewElement.getSimpleName());
                if (bindViewElement.getKind() == ElementKind.FIELD) {
                    VariableElement fieldElement = (VariableElement) bindViewElement;
                    // 注解在属性之上，属性节点父节点是类节点
                    TypeElement enclosingElement = (TypeElement) fieldElement.getEnclosingElement();
                    // 如果map集合中的key：类节点存在，直接添加属性
                    if (tempBindViewMap.containsKey(enclosingElement)) {
                        tempBindViewMap.get(enclosingElement).add(fieldElement);
                    } else {
                        List<VariableElement> fields = new ArrayList<>();
                        fields.add(fieldElement);
                        tempBindViewMap.put(enclosingElement, fields);
                    }
                }
            }
        }
    }

    private void setOnClickMap(Set<? extends Element> onClickElements) {
        if (CollectionUtils.isNotEmpty(onClickElements)) {
            for (Element onClickElement : onClickElements) {
                messager.printMessage(Diagnostic.Kind.NOTE, "@OnClick >>> " + onClickElement.getSimpleName());
                if (onClickElement.getKind() == ElementKind.METHOD) {
                    ExecutableElement methodElement = (ExecutableElement) onClickElement;
                    // 注解在属性之上，属性节点父节点是类节点
                    TypeElement enclosingElement = (TypeElement) methodElement.getEnclosingElement();
                    // 如果map集合中的key：类节点存在，直接添加属性
                    if (tempOnClickMap.containsKey(enclosingElement)) {
                        tempOnClickMap.get(enclosingElement).add(methodElement);
                    } else {
                        List<ExecutableElement> fields = new ArrayList<>();
                        fields.add(methodElement);
                        tempOnClickMap.put(enclosingElement, fields);
                    }
                }
            }
        }
    }
}

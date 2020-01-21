package com.tck.compiler.butterknife;

import com.google.auto.service.AutoService;
import com.tck.libannotation.butterknife.BindView;
import com.tck.libannotation.butterknife.OnClick;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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


            return true;
        }


        return false;
    }

    private void setBindViewMap(Set<? extends Element> bindViewElements) {
        if (CollectionUtils.isNotEmpty(bindViewElements)) {
            for (Element bindViewElement : bindViewElements) {
                messager.printMessage(Diagnostic.Kind.NOTE, "@BindView >>> " + bindViewElement.getSimpleName());
                if (bindViewElement.getKind() == ElementKind.FIELD) {
                    VariableElement methodElement = (VariableElement) bindViewElement;
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
                }
            }
        }
    }
}

package net.funol.databinding.watchdog.compiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import net.funol.databinding.watchdog.annotations.NotifyThis;
import net.funol.databinding.watchdog.annotations.WatchThis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Created by ZHAOWEIWEI on 2017/1/9.
 */
@AutoService(Processor.class)
public class WatchdogProcessor extends AbstractProcessor {

    private Elements mElementUtils;
    private Filer mFiler;
    private Map<String, List<Element>> mTypeSpecBuilderMap;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mElementUtils = processingEnv.getElementUtils();
        mFiler = processingEnv.getFiler();
        mTypeSpecBuilderMap = new HashMap<>();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(WatchThis.class.getName());
        types.add(NotifyThis.class.getName());
        return types;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (Element element : roundEnv.getElementsAnnotatedWith(WatchThis.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                String className = element.getEnclosingElement().getSimpleName().toString();
                List<Element> elements = mTypeSpecBuilderMap.get(className);
                if (elements == null) {
                    elements = new ArrayList<>();
                }
                elements.add(element);
                mTypeSpecBuilderMap.put(className, elements);
            }
        }

        Set<String> keys = mTypeSpecBuilderMap.keySet();

        for (String key : keys) {
            List<Element> elements = mTypeSpecBuilderMap.get(key);
            TypeSpec.Builder iPropertyChangeCallbacksBuilder = generatePropertyChangeCallbacksInterface(elements.get(0));
            for (Element element : elements) {
                iPropertyChangeCallbacksBuilder.addMethod(generatePropertyChangeCallbacksMethod(element).build());
            }
            writeToFile(getPackageName(elements.get(0)) + Util.WATCHDOG_PACKAGE_NAME_SUFFIX, iPropertyChangeCallbacksBuilder.build());
        }

        return false;
    }

    public TypeSpec.Builder generatePropertyChangeCallbacksInterface(Element element) {
        return TypeSpec.interfaceBuilder(Util.getCallbackInterfaceName(element.getEnclosingElement().getSimpleName().toString()))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
    }

    public MethodSpec.Builder generatePropertyChangeCallbacksMethod(Element element) {
        return MethodSpec.methodBuilder(element.getSimpleName().toString())
                .addModifiers(Modifier.PUBLIC)
                .addModifiers(Modifier.ABSTRACT)
                .returns(TypeName.VOID)
                .addAnnotation(NotifyThis.class)
                .addParameter(TypeName.get(element.asType()), "observableField")
                .addParameter(TypeName.INT, "fieldId");
    }

    private void writeToFile(String packageName, TypeSpec typeSpec) {
        try {
            JavaFile.builder(packageName, typeSpec)
                    .build()
                    .writeTo(mFiler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getPackageName(Element element) {
        return mElementUtils.getPackageOf(element).getQualifiedName().toString();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    public static class Util {

        private static final String WATCHDOG_PACKAGE_NAME_SUFFIX = ".watchdog";

        public static String getCallbackInterfaceName(String className) {
            return "I" + className + "Callbacks";
        }
    }

}

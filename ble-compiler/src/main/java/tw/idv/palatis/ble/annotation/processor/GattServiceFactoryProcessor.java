package tw.idv.palatis.ble.annotation.processor;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import tw.idv.palatis.ble.annotation.GattService;

@AutoService(Processor.class)
public class GattServiceFactoryProcessor extends AbstractProcessor {
    private Elements mElementUtils;
    private Filer mFiler;
    private Messager mMessager;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        mMessager = env.getMessager();
        mElementUtils = env.getElementUtils();
        mFiler = env.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(GattService.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GattService.class);
        if (elements.isEmpty())
            return false;

        final HashMap<String, ArrayList<Element>> factoryServiceMap = extractFactoryClasses(elements);

        for (final String factoryClass : factoryServiceMap.keySet())
            processServiceFactory(factoryClass, factoryServiceMap.get(factoryClass));

        return true;
    }

    private void processServiceFactory(final String factoryClass, final ArrayList<Element> elements) {
        final String factoryPackageName = factoryClass.substring(0, factoryClass.lastIndexOf('.'));
        final String factoryClassName = factoryClass.substring(factoryClass.lastIndexOf('.') + 1);

        final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(factoryClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ClassName.bestGuess("tw.idv.palatis.ble.BluetoothGattServiceFactory"))
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC).build());

        final ClassName logClass = ClassName.bestGuess("android.util.Log");

        final CodeBlock.Builder debugCtorCodeBuilder = CodeBlock.builder()
                .addStatement("$T.d(\"BLEgen\", $S + \" handles\")", logClass, factoryClass)
                .beginControlFlow("if ($N)", "debug");

        final MethodSpec.Builder newInstanceBuilder = MethodSpec.methodBuilder("newInstance")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Nullable.class)
                .returns(ClassName.bestGuess("tw.idv.palatis.ble.services.BluetoothGattService"))
                .addParameter(
                        ParameterSpec.builder(ClassName.bestGuess("tw.idv.palatis.ble.BluetoothDevice"), "device", Modifier.FINAL)
                                .addAnnotation(NonNull.class)
                                .build()
                )
                .addParameter(
                        ParameterSpec.builder(ClassName.bestGuess("android.bluetooth.BluetoothGattService"), "nativeService", Modifier.FINAL)
                                .addAnnotation(NonNull.class)
                                .build()
                )
                .addStatement("final $T uuid = $N", UUID.class, "nativeService.getUuid()");

        for (final Element element : elements) {
            debugCtorCodeBuilder.addStatement("$T.d(\"BLEgen\", \"    \" + $S)", logClass, element);
            newInstanceBuilder.addCode("if (uuid.equals($T.UUID_SERVICE))\n\treturn new $T(device, nativeService);\n", element, element);
        }

        newInstanceBuilder.addCode("return null;\n");
        debugCtorCodeBuilder.endControlFlow();

        classBuilder.addMethod(newInstanceBuilder.build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(TypeName.BOOLEAN, "debug", Modifier.FINAL)
                        .addCode(debugCtorCodeBuilder.build())
                        .build());

        try {
            JavaFile.builder(factoryPackageName, classBuilder.build())
                    .addFileComment("Generated code from annotation compiler. Do not modify!")
                    .build().writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("Unable to write register %s", e.getMessage()));
        }
    }

    private HashMap<String, ArrayList<Element>> extractFactoryClasses(final Set<? extends Element> elements) {
        final HashMap<String, ArrayList<Element>> factoryServiceMap = new HashMap<>();
        for (final Element element : elements) {
            final PackageElement defaultPackage = mElementUtils.getPackageOf(element);
            final String defaultFactoryClass = defaultPackage.getQualifiedName() + "." + GattService.DEFAULT_CLASS_NAME;

            final GattService annotation = element.getAnnotation(GattService.class);
            final String preferredFactoryClass = TextUtils.isEmpty(annotation.factoryClass()) ? annotation.value() : annotation.factoryClass();

            final String factoryClass = TextUtils.isEmpty(preferredFactoryClass) ? defaultFactoryClass : preferredFactoryClass;

            if (!factoryServiceMap.containsKey(factoryClass))
                factoryServiceMap.put(factoryClass, new ArrayList<Element>());

            factoryServiceMap.get(factoryClass).add(element);
        }
        return factoryServiceMap;
    }
}

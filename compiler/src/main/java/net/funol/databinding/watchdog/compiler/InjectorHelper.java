package net.funol.databinding.watchdog.compiler;

import android.databinding.Observable;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import net.funol.databinding.watchdog.Injector;
import net.funol.databinding.watchdog.Watchdog;
import net.funol.databinding.watchdog.annotations.WatchThis;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Created by ZHAOWEIWEI on 2017/3/31.
 */

public class InjectorHelper {

    static final String INJECT_METHOD_NAME = "inject";
    static final String BE_WATCHED_PARAM_NAME = "beWatched";
    static final String BE_NOTIFIED_PARAM_NAME = "beNotified";
    static final String ADD_ON_PROPERTY_CHANGED_CALLBACK = "addOnPropertyChangedCallback";

    static final String OBSERVABLE_FIELD = "observableField";
    static final String FIELD_ID = "fieldId";

    public static String getInjectorPackageName(Element element) {
        return Watchdog.getWatchdogPackage(element.getEnclosingElement().toString());
    }

    public static String getInjectorClassName(Element element) {
        return Watchdog.getInjectorClassName(element.getSimpleName().toString());
    }

    public static TypeSpec.Builder generateInjector(Element element) {
        return generateInjector(element, null);
    }

    public static TypeSpec.Builder generateInjector(Element element, ClassName superType) {

        TypeName beWatchedTypeName = TypeName.get(element.asType());
        TypeName beNotifiedTypeName = ClassName.get(CallbackHelper.getCallbackPackageName(element), CallbackHelper.getCallbackInterfaceName(element));

        TypeSpec.Builder injectorClass = generateInjectorClass(getInjectorClassName(element), beWatchedTypeName, beNotifiedTypeName);
        MethodSpec.Builder injectMethod = generateInjectMethod(TypeVariableName.get("W"), TypeVariableName.get("N"));

        if (superType != null) {
            injectorClass.superclass(ParameterizedTypeName.get(superType, TypeVariableName.get("W"), TypeVariableName.get("N")));
            injectMethod.addCode("super.$N($N, $N);\n", INJECT_METHOD_NAME, BE_WATCHED_PARAM_NAME, BE_NOTIFIED_PARAM_NAME);
        } else {
            injectorClass.addSuperinterface(ParameterizedTypeName.get(ClassName.get(Injector.class), TypeVariableName.get("W"), TypeVariableName.get("N")));
        }

        for (Element field : element.getEnclosedElements()) {
            if (field.getAnnotation(WatchThis.class) != null) {
                // method name
                String methodName = field.getAnnotation(WatchThis.class).method();
                methodName = methodName.equals("") ? field.getSimpleName().toString() : methodName;
                // observable TypeName
                TypeName paramTypeName = TypeName.get(field.asType());
                // generate anonymous callback
                TypeSpec propertyChangeCallback = generatePropertyChangeCallback(methodName, paramTypeName).build();
                // add property change callback
                injectMethod.addStatement("$N.$N.$N($L)", BE_WATCHED_PARAM_NAME, field.getSimpleName(), ADD_ON_PROPERTY_CHANGED_CALLBACK, propertyChangeCallback);
            }
        }
        injectorClass.addMethod(injectMethod.build());
        return injectorClass;
    }

    public static TypeSpec.Builder generateInjectorClass(String className, TypeName beWatchedTypeName, TypeName beNotifiedTypeName) {
        return TypeSpec.classBuilder(className)
                .addTypeVariable(TypeVariableName.get("W", beWatchedTypeName))
                .addTypeVariable(TypeVariableName.get("N", beNotifiedTypeName))
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .build())
                .addModifiers(Modifier.PUBLIC);
    }

    public static MethodSpec.Builder generateInjectMethod(TypeName beWatched, TypeName beNotified) {
        return MethodSpec.methodBuilder(INJECT_METHOD_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(ParameterSpec.builder(beWatched, BE_WATCHED_PARAM_NAME).build())
                .addParameter(ParameterSpec.builder(beNotified, BE_NOTIFIED_PARAM_NAME, Modifier.FINAL).build());
    }

    public static TypeName getInjectorSuperType(Elements mElementUtils, Types mTypesUtils, Element superElement) {
        // class name
        String originClassName = superElement.toString();
        // guess callback interface name
        String guessInjectorName = getInjectorClassName(superElement);

        TypeElement guessInjectorTypeElement = mElementUtils.getTypeElement(guessInjectorName);

        if (guessInjectorTypeElement != null && mTypesUtils.isSubtype(guessInjectorTypeElement.asType(), mElementUtils.getTypeElement(Injector.class.getName()).asType())) {
            return TypeName.get(guessInjectorTypeElement.asType());
        } else {
            return null;
        }
    }

    public static TypeSpec.Builder generatePropertyChangeCallback(String callbackMethodName, TypeName paramTypeName) {
        return TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(Observable.OnPropertyChangedCallback.class)
                .addMethod(MethodSpec.methodBuilder("onPropertyChanged")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeName.VOID)
                        .addParameter(ClassName.get(Observable.class), OBSERVABLE_FIELD)
                        .addParameter(TypeName.INT, FIELD_ID)
                        .addCode(CodeBlock.builder()
                                .beginControlFlow("if ($N != null)", BE_NOTIFIED_PARAM_NAME)
                                .addStatement("$N.$N(($T)$N,$N)", BE_NOTIFIED_PARAM_NAME, callbackMethodName, paramTypeName, OBSERVABLE_FIELD, FIELD_ID)
                                .endControlFlow()
                                .build())
                        .build());
    }
}

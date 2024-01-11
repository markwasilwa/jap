package biz.duka;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("biz.duka.BuildProperty") // Only process @BuildProperty annotations
public class DukaAnnotationProcessor  extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("DukaAnnotationProcessor.process()");

        for(TypeElement annotation: annotations) {
            // Obtain all instances annotated by the annotation.
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

            // Check whether the annotation starts with "set" and only one argument is required.
            Map<Boolean, List<Element>> annotatedMethods = annotatedElements.stream().collect(
                    Collectors.partitioningBy(element ->
                            ((ExecutableType) element.asType()).getParameterTypes().size() == 1
                                && element.getSimpleName().toString().startsWith("set")));

            List<Element> setters = annotatedMethods.get(true);
            List<Element> othersMethods = annotatedMethods.get(false);

            // Print the case where the annotation was used incorrectly.
            othersMethods.forEach(element ->
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@BuilderProperty must be applied to a setXxx method with a single argument", element));

            if (setters.isEmpty()) {
                continue;
            }

            Map<String, List<Element>> groupMap = new HashMap<>();

            // Group by fully-qualified class name. A builder is created for each class
            setters.forEach(setter -> {
               // Fully-qualified class name
               String className = ((TypeElement) setter
                       .getEnclosingElement()).getQualifiedName().toString();
               List<Element> elements = groupMap.get(className);
               if (elements != null) {
                   elements.add(setter);
               } else {
                   List<Element> newElements = new ArrayList<>();
                   newElements.add(setter);
                   groupMap.put(className, newElements);
               }
            });

            groupMap.forEach((groupSetterKey, groupSetterValue) -> {
                // Obtain the class name SimpleName and the input paremeters of the set() method.
                Map<String, String> setterMap = groupSetterValue.stream().collect(Collectors.toMap(
                        setter -> String.valueOf(setter.getSimpleName()),
                        setter -> ((ExecutableType) setter.asType()).getParameterTypes().get(0).toString()
                ));
                try {
                    // Assemble the XXXBuild class and create the corresponding class file
                    writeBuilderFile(groupSetterKey, setterMap);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        // false means other processors can continue processing the annotations after the current processor is done.
        // true means other processor won't process the annotations after the current processor is done.
        return true;
    }

    private void writeBuilderFile(String className, Map<String, String> setterMap) throws IOException {
        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        String simpleClassName = className.substring(lastDot + 1);
        String builderClassName = className +  "Builder";
        String builderSimpleClassName = builderClassName
                .substring(lastDot + 1);
        JavaFileObject builderFile = processingEnv.getFiler()
                .createSourceFile(builderClassName);

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            if (packageName != null) {
                out.print("package ");
                out.print(packageName);
                out.println(";");
                out.println();
            }

            out.print("public class ");
            out.print(builderSimpleClassName);
            out.print(" {");
            out.println();

            out.print(" private ");
            out.print(simpleClassName);
            out.print(" object = new ");
            out.print(simpleClassName);
            out.print("();");
            out.println();

            out.print(" public ");
            out.print(simpleClassName);
            out.print(" build() {");
            out.print(" return object;");
            out.print(" }");
            out.println();

            setterMap.entrySet().forEach(setter -> {
                String methodName = setter.getKey();
                String argumentType = setter.getValue();

                out.println("   public ");
                out.println(builderSimpleClassName);
                out.println("   ");
                out.println(methodName);

                out.print("(");

                out.print(argumentType);
                out.println(" value) {");
                out.println("       object.");
                out.println(methodName);
                out.println("(value);");
                out.println("   return this;");
                out.println("   }");
                out.println();
            });

            out.println("}");
        }
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        System.out.println("----------");
        System.out.println(processingEnv.getOptions());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}

package org.checkerframework.common.wholeprograminference.scenelib;

import com.google.common.collect.ImmutableMap;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.signature.qual.BinaryName;
import org.checkerframework.common.wholeprograminference.SceneToStubWriter;
import org.checkerframework.common.wholeprograminference.WholeProgramInference.OutputFormat;
import org.checkerframework.common.wholeprograminference.WholeProgramInferenceScenesStorage.AnnotationsInContexts;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.Pair;
import org.checkerframework.javacutil.UserError;
import scenelib.annotations.Annotation;
import scenelib.annotations.el.AClass;
import scenelib.annotations.el.AField;
import scenelib.annotations.el.AMethod;
import scenelib.annotations.el.AScene;
import scenelib.annotations.el.ATypeElement;
import scenelib.annotations.el.DefException;
import scenelib.annotations.io.IndexFileWriter;

/**
 * scene-lib (from the Annotation File Utilities) doesn't provide enough information to usefully
 * print stub files: it lacks information about what is and is not an enum, about the base types of
 * variables, and more.
 *
 * <p>This class wraps AScene but provides access to that missing information. This allows us to
 * preserve the code that generates .jaif files, while allowing us to sanely and safely keep the
 * information we need to generate stubs.
 *
 * <p>It would be better to write as a subclass of AScene.
 */
public class ASceneWrapper {

    /** The AScene being wrapped. */
    private AScene theScene;

    /** The classes in the scene. */
    private Map<@BinaryName String, AClassWrapper> classes = new HashMap<>();

    /**
     * Constructor. Pass the AScene to wrap.
     *
     * @param theScene the scene to wrap
     */
    public ASceneWrapper(AScene theScene) {
        this.theScene = theScene;
    }

    /**
     * Fetch the classes in this scene, represented as AClassWrapper objects.
     *
     * @return an immutable map from binary names to AClassWrapper objects
     */
    public Map<@BinaryName String, AClassWrapper> getClasses() {
        return ImmutableMap.copyOf(classes);
    }

    /**
     * Removes the specified annotations from an AScene.
     *
     * @param scene the scene from which to remove annotations
     * @param annosToRemove annotations that should not be added to .jaif or stub files
     */
    private void removeAnnosFromScene(AScene scene, AnnotationsInContexts annosToRemove) {
        for (AClass aclass : scene.classes.values()) {
            for (AField field : aclass.fields.values()) {
                removeAnnosFromATypeElement(field.type, TypeUseLocation.FIELD, annosToRemove);
            }
            for (AMethod method : aclass.methods.values()) {
                removeAnnosFromATypeElement(
                        method.returnType, TypeUseLocation.RETURN, annosToRemove);
                removeAnnosFromATypeElement(
                        method.receiver.type, TypeUseLocation.RECEIVER, annosToRemove);
                for (AField param : method.parameters.values()) {
                    removeAnnosFromATypeElement(
                            param.type, TypeUseLocation.PARAMETER, annosToRemove);
                }
            }
        }
    }

    /**
     * Removes the specified annotations from an ATypeElement.
     *
     * @param typeElt the type element from which to remove annotations
     * @param loc the location where typeEl in used
     * @param annosToRemove annotations that should not be added to .jaif or stub files
     */
    private void removeAnnosFromATypeElement(
            ATypeElement typeElt, TypeUseLocation loc, AnnotationsInContexts annosToRemove) {
        String annosToRemoveKey = typeElt.description.toString() + typeElt.tlAnnotationsHere;
        Set<String> annosToRemoveForLocation = annosToRemove.get(Pair.of(annosToRemoveKey, loc));
        if (annosToRemoveForLocation != null) {
            Set<Annotation> annosToRemoveHere = new HashSet<>();
            for (Annotation anno : typeElt.tlAnnotationsHere) {
                if (annosToRemoveForLocation.contains(anno.def().toString())) {
                    annosToRemoveHere.add(anno);
                }
            }
            typeElt.tlAnnotationsHere.removeAll(annosToRemoveHere);
        }

        // Recursively remove ignored annotations from inner types
        for (ATypeElement innerType : typeElt.innerTypes.values()) {
            removeAnnosFromATypeElement(innerType, loc, annosToRemove);
        }
    }

    /**
     * Write the scene wrapped by this object to a file at the given path.
     *
     * @param jaifPath the path of the file to be written, ending in .jaif
     * @param annosToIgnore which annotations should be ignored in which contexts
     * @param outputFormat the output format to use. If a format other than JAIF is selected, the
     *     path will be modified to match.
     */
    public void writeToFile(
            String jaifPath, AnnotationsInContexts annosToIgnore, OutputFormat outputFormat) {
        AScene scene = theScene.clone();
        removeAnnosFromScene(scene, annosToIgnore);
        scene.prune();
        String filepath = jaifPath;
        if (outputFormat == OutputFormat.STUB) {
            filepath = jaifPath.replace(".jaif", ".astub");
        }
        new File(filepath).delete();
        if (!scene.isEmpty()) {
            // Only write non-empty scenes into files.
            try {
                switch (outputFormat) {
                    case STUB:
                        SceneToStubWriter.write(this, new FileWriter(filepath));
                        break;
                    case JAIF:
                        IndexFileWriter.write(scene, new FileWriter(filepath));
                }
            } catch (IOException e) {
                throw new UserError("Problem while writing %s: %s", filepath, e.getMessage());
            } catch (DefException e) {
                throw new BugInCF(e);
            }
        }
    }

    /**
     * Obtain the representation of the given class, which can be further operated on to e.g. add
     * information about a method. This method also updates the metadata stored about the class
     * using the given ClassSymbol, if it is non-null.
     *
     * <p>Results are interned.
     *
     * @param className the binary name of the class to be added to the scene
     * @param classSymbol the element representing the class, used for adding data to the
     *     AClassWrapper returned by this method. If it is null, the AClassWrapper's data will not
     *     be updated.
     * @return an AClassWrapper representing that class
     */
    public AClassWrapper vivifyClass(
            @BinaryName String className, @Nullable ClassSymbol classSymbol) {
        AClassWrapper wrapper;
        if (classes.containsKey(className)) {
            wrapper = classes.get(className);
        } else {
            AClass aClass = theScene.classes.getVivify(className);
            wrapper = new AClassWrapper(aClass);
            classes.put(className, wrapper);
        }

        // updateClassMetadata must be called on both paths (cache hit and cache miss) because the
        // second parameter could have been null when the first miss occurred.
        // Different visit methods in CFAbstractTransfer call WPI in different ways.  Only some
        // provide the metadata, and the visit order isn't known ahead of time.
        // Since it is not used until the end of WPI, it being unavailable during WPI is not a
        // problem.
        if (classSymbol != null) {
            updateClassMetadata(wrapper, classSymbol);
        }
        return wrapper;
    }

    /**
     * Updates the metadata stored in AClassWrapper for the given class.
     *
     * @param aClassWrapper the class representation in which the metadata is to be updated
     * @param classSymbol the class for which to update metadata
     */
    private void updateClassMetadata(AClassWrapper aClassWrapper, ClassSymbol classSymbol) {
        if (classSymbol.isEnum()) {
            if (!aClassWrapper.isEnum()) {
                List<VariableElement> enumConstants = new ArrayList<>();
                for (Element e : ((TypeElement) classSymbol).getEnclosedElements()) {
                    if (e.getKind() == ElementKind.ENUM_CONSTANT) {
                        enumConstants.add((VariableElement) e);
                    }
                }
                aClassWrapper.setEnumConstants(enumConstants);
            }
        }

        aClassWrapper.setTypeElement(classSymbol);
    }

    /**
     * Avoid using this if possible; use the other methods of this class unless you absolutely need
     * an AScene.
     *
     * @return the representation of this scene using only the AScene
     */
    public AScene getAScene() {
        return theScene;
    }
}
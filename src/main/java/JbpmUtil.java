import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.json.simple.JSONObject;
import org.reflections.Reflections;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JbpmUtil {

    public static void getAllFiles(String projectName) throws IOException, GitAPIException {

        File clonedPath = new File("src/main/resources/projects/" + projectName);
        String repoURI = "https://staging-jbpm.moneylion.io//business-central/git/MySpace/" + projectName;
        Git git = Git.cloneRepository()
                .setURI(repoURI)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider("wbadmin", "wbadmin"))
                .setDirectory(clonedPath)
                .call();


        String projectName_lower = projectName.toLowerCase();

        File classPath = new File("src/main/java/com/myspace/" + projectName_lower);
        FileUtils.deleteDirectory(classPath);
        FileUtils.moveDirectory(new File(clonedPath.getPath() + "/src/main/java/com/myspace/" + projectName_lower), classPath);


        File resourcePath = new File("src/main/resources/com/myspace/" + projectName_lower);
        FileUtils.deleteDirectory(resourcePath);
        FileUtils.moveDirectory(new File(clonedPath.getPath() + "/src/main/resources/com/myspace/" + projectName_lower), resourcePath);

        /** delete the cloned project folder so that we can clone the project into the same destination next time */
        FileUtils.deleteDirectory(clonedPath);
    }

    public static String getMainClazzName(String projectName, String processName) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document processDoc = db.parse(new File("src/main/resources/com/myspace/" + projectName.toLowerCase() + "/" + processName + ".bpmn"));
        processDoc.getDocumentElement().normalize();

        NodeList nodeList = processDoc.getElementsByTagName("bpmn2:itemDefinition");
        String mainClazzName = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            NamedNodeMap attributes = node.getAttributes();
            Node id = attributes.getNamedItem("id");
            if (id.getNodeValue().equals("_inputItem")) {
                mainClazzName = attributes.getNamedItem("structureRef").getNodeValue();
                break;
            }
        }
        return mainClazzName;
    }

    public static void configureKsession(String projectName) throws ParserConfigurationException, IOException, SAXException, TransformerException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document kmoduleDoc = db.parse(new File("src/main/resources/META-INF/kmodule.xml"));
        kmoduleDoc.getDocumentElement().normalize();

        NodeList nodeList = kmoduleDoc.getElementsByTagName("kbase");
        int i;
        for (i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            NamedNodeMap attributes = node.getAttributes();
            Node name = attributes.getNamedItem("name");
            if (name.getNodeValue().equals(projectName.toLowerCase() + "_kbase")) {
                break;
            }
        }

        if (i == nodeList.getLength()) {
            Element newKbaseElement = kmoduleDoc.createElement("kbase");
            newKbaseElement.setAttribute("name", projectName.toLowerCase() + "_kbase");
            newKbaseElement.setAttribute("packages", "com.myspace." + projectName.toLowerCase());

            Element newKsessionElement = kmoduleDoc.createElement("ksession");
            newKsessionElement.setAttribute("name", projectName.toLowerCase() + "_ksession");

            newKbaseElement.appendChild(newKsessionElement);
            kmoduleDoc.getFirstChild().appendChild(newKbaseElement);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(kmoduleDoc);
            StreamResult result = new StreamResult(new File("src/main/resources/META-INF/kmodule.xml"));
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(source, result);
        }
    }

    public static Serializable getAllDataObjects(String projectName, JSONObject inputData, String mainClazzName) throws InstantiationException, IllegalAccessException {
        if (mainClazzName == null) {
            return null;
        }

        Reflections reflections = new Reflections("com.myspace." + projectName.toLowerCase());
        Set<Class<? extends  Serializable>> clazz_set = reflections.getSubTypesOf(java.io.Serializable.class);
        Map<String, Class<? extends  java.io.Serializable>> clazz_map = new HashMap<>();
        for (Class<? extends  java.io.Serializable> clazz : clazz_set) {
            clazz_map.put(clazz.getName(), clazz);
        }

        Serializable obj = JbpmUtil.getDataObject(clazz_map, inputData, mainClazzName);
        if (obj == null) {
            return null;
        }

        return obj;
    }

    private static Serializable getDataObject(Map<String, Class<? extends  java.io.Serializable>> clazz_map, JSONObject inputData, String mainClazzName) throws InstantiationException, IllegalAccessException {

        if (inputData == null) {
            return null;
        }

        Class<? extends java.io.Serializable> clazz = clazz_map.get(mainClazzName);
        Serializable mainObj = clazz.newInstance();

        Field[] declaredFields = clazz.getDeclaredFields();
        for (int i = 0; i < declaredFields.length; i++) {

            Field field = declaredFields[i];
            field.setAccessible(true);
            String fieldName = field.getName();

            if (!fieldName.equals("serialVersionUID")) {
                Class<?> fieldType = field.getType();
                String clazzName = fieldType.getName();

                if (clazzName.startsWith("java.lang.")) {
                    field.set(mainObj, fieldType.cast(inputData.get(fieldName)));
                }
                else if (clazzName.startsWith("java.util.")) {
                    field.set(mainObj, inputData.get(fieldName));
                }
                else if (clazzName.startsWith("com.myspace.")) {

                    JSONObject inputData_temp = (JSONObject) inputData.get(fieldName);
                    Serializable obj = JbpmUtil.getDataObject(clazz_map, inputData_temp, clazzName);
                    if (obj == null) {
                        return null;
                    }
                    field.set(mainObj, fieldType.cast(obj));
                }
                else {
                    System.out.println("DataObjectReflectionUtil Error: " + clazzName + " not recognised");
                }
            }
        }
        return mainObj;
    }
}

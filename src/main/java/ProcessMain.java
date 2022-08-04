import java.io.*;
import java.util.*;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

public class ProcessMain {

    public static void main(String[] args) throws InstantiationException, IllegalAccessException, IOException, ParseException, ParserConfigurationException, SAXException, GitAPIException, TransformerException {
        /** assuming all projects are within same space i.e. "myspace" */

//        String projectName = "myProject";               // project name for which the processes and rules are defined in
//        String processName = "cdpProcess";

        /** get project and process name from input */
        Scanner sc= new Scanner(System.in);
        System.out.print("Enter project name: ");
        String projectName= sc.nextLine();
        System.out.print("Enter process name: ");
        String processName= sc.nextLine();
        sc.close();

        String processID = projectName.toLowerCase() + "." + processName;
        String kSessionName = projectName.toLowerCase() + "_ksession";

        JbpmUtil.getAllFiles(projectName);          /** getting process and rule files from jbpm */
        JbpmUtil.configureKsession(projectName);    /** configure kbase/ksession in kmodule.xml, ONLY necessary for new project */

        /** parse input data */
        JSONParser jsonParser = new JSONParser();
        JSONObject cdp_users = (JSONObject) jsonParser.parse(new FileReader("src/main/resources/json/sample_cdp2.json"));
        JSONArray cdp_users_arr = (JSONArray) cdp_users.get("cdp_users");

        /** set up kie session to run process and variables to be passed in */
        KieContainer kc = KieServices.Factory.get().getKieClasspathContainer();
        KieSession ksession = kc.newKieSession(kSessionName);

        ArrayList<Object> output = new ArrayList<>();
        Map<String, Object> variables = new HashMap();
        variables.put("output", output);

        /** getting the main data object from bpmn file */
        String mainClazzName = JbpmUtil.getMainClazzName(projectName, processName);

        /** run the process for each user */
        for(Object cdp_user: cdp_users_arr) {
            /** do reflection recursively starting from the main data object */
            Serializable mainObj = JbpmUtil.getAllDataObjects(projectName, (JSONObject) cdp_user, mainClazzName);
            if (mainObj == null) {
                continue;
            }
            variables.put("input", mainObj);
            ksession.startProcess(processID, variables);
        }

        System.out.println("output = " + output);
    }

}
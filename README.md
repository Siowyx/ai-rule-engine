# ai-rule-engine
An internship project at MoneyLion

### Aim
To be able to run processes and rules created with JBPM

### How it works
1. Prompts user for project name and process name (assumes project is in "myspace", currently no validation of the user input)
2. Pulls all files within the project from JBPM into a placeholder folder named "projects"
3. Moves only the relevant process files, rule files, and data object files into appropriate folders i.e. java/com/myspace/_project_name_ and resources/com/myspace/_project_name_
4. Checks if kbase/ksession is set up for the project, if not, append kmodule.xml
5. Sets up ksession and initialize an array to be passed into the process as output
6. Identifies the main data object from the process file (assumes that the process data is named "input")
7. Does reflection recursively starting from the main data object, setting all the field values of each object according to the input json file
8. Run the process as many times as the number of user data
9. Prints out the results as an array
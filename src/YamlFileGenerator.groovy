import groovy.json.JsonSlurper
import groovyx.net.http.RESTClient
import java.nio.file.Path
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions
import static groovyx.net.http.ContentType.URLENC


 /*@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')
 @Grab(group='org.yaml', module='snakeyaml', version='1.17')
 @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.2.1')
 //@Grab(group='commons-cli', module='commons-cli', version='1.3.1')
 @Grab(group='org.apache.httpcomponents', module='httpcore', version='4.4.5')
 @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.2')
 @Grab(group='commons-logging', module='commons-logging', version='1.2')
 @Grab(group='net.sf.json-lib', module='json-lib', version='2.4', classifier='jdk15')
 @Grab(group='xml-resolver', module='xml-resolver', version='1.2')
 @Grab(group='org.apache.commons', module='commons-collections4', version='4.1')
 @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.22')
 @Grab(group='xerces', module='xercesImpl', version='2.6.2')*/

 
def cli = new CliBuilder(usage: 'groovy CreateYmlFile.groovy [options]',
header: 'Options:')

/*Run Time Parameters 
 *-------------------    
 */
cli.with {
	t longOpt: 'template', 
	'Template URI (override) Complete xml template file path  eg, C:/NifiTemplates/Template.xml',
	args:1, argName:'uri', type:String.class
	f longOpt: 'file',
	'Properties File',
	args:1, argName:'name', type:String.class
	n longOpt: 'nifiapi', 
	'NiFi REST API (override), e.g. http://example.com:9090/nifi-api defaults to http://localhost:8080/nifiapi This works only on unsecured nifi',
	args:1, argName:'http://host:port', type:String.class
	f1 longOpt: 'fileout',
	'yml Output file, eg. MyOutputFile.yml ',
	args:1, argName:'name', type:String.class
	cn longOpt: 'clusteropts',
	'cluster opts expects NODE or NCM or BOTH Default is NODE',
	args:1, argName:'name', type:String.class
	u longOpt: 'username', 'username to authenticate with NiFi server',
	args:1, argName:'user', type:String.class
	p longOpt: 'password', 'password to authenticate with NiFi server',
	args:1, argName:'pass', type:String.class

	
}

/*
 *Variables to hold run time parameters
 *-------------------------------------
 */
                           

def opts = cli.parse(args)
def propertiesFile;
def outputFile;
def node ;
def nifiapiurl ;
def templateUri;


/*Error Handling for run time parameters
--------------------------------------*/

/*Property File check
------------------- */
if (opts.file) {
	propertiesFile = opts.file
} else {
	println "ERROR: Missing a file argument\n"
	cli.usage()
	System.exit(-1)
}

/*Template file chk
-----------------*/

if (opts.template) {
  templateUri= opts.template
File f = new File(templateUri)
  def scheme = f.toURI().scheme
  if (!scheme) {
	  // assume a local file
	  templateUri= 'file:' + templateUri
  }
} else {
  println "ERROR: Missing a file argument\n"
  cli.usage()
  System.exit(-1)
}

/*Out File Check
--------------*/

if (opts.fileout) {
	outputFile = opts.fileout
} else {
	println "ERROR: Missing a file argument\n"
	cli.usage()
	System.exit(-1)
}


/*Nifi api url
 * 
------------------------------------------*/

def nifiuri;
if(opts.nifiapi){
	nifiuri=opts.nifiapi

	//Check for Secured/Unsecured nifi
	if (nifiuri.startsWith("https")){

		//Secured Connection
		nifiapiurl = new RESTClient(opts.nifiapi)

		//Secured connection requires username and password to connect
		def user  = opts.username
		def pass  = opts.password
		nifiapiurl.ignoreSSLIssues()
		assert user : 'Authorization user must be provided for Secured Nifi'
		assert pass : 'Authorization password must be provided for Secured Nifi'
		def authbody = [username : "$user", password : "$pass"]


		resp = nifiapiurl.post (
				path: "access/token",
				body: authbody,
				requestContentType: URLENC
				)

		assert resp.status == 201


		//get response bearer token and set our header
		nifiapiurl.defaultRequestHeaders.'Authorization' = "Bearer " + resp.data.text


	}else{
		//unsecured Connection
		nifiapiurl = new RESTClient(opts.nifiapi)

	}

}else{

		//if url is not specified defaulted to localhost 

		nifiuri="http://localhost:8080/nifi-api"
		nifiapiurl = new RESTClient("http://localhost:8080/nifi-api")
}

/*Cluster Options
---------------*/

if(opts.clusteropts.equalsIgnoreCase("NODE")||opts.clusteropts.equalsIgnoreCase("NCM")||opts.clusteropts.equalsIgnoreCase("BOTH")){

node=opts.clusteropts.toUpperCase()
}else{

node="NODE"
}

/*Define the root variable
------------------------*/

def  y = [:];
def  t = new XmlSlurper().parse(templateUri)
def  processorGroupMap = [:];
def controllerServicesMap=[:];


/*Load Properties file in a Map 
 *-----------------------------
 * Load the process group related properties in processorGroupMap
 * Load control services related properties in controllerServicesMap
 */


loadPropertiesMap(propertiesFile,processorGroupMap,controllerServicesMap)




/*Create a Data Structure to hold yaml file
-----------------------------------------*/

y.nifi = [:]

y.nifi.url= nifiuri;

y.nifi.clientId='REPLACEME';

y.nifi.templateUri = templateUri

y.nifi.templateName = t.name.text()

y.nifi.gracefullShutDown=Boolean.TRUE;

y.nifi.undeploy=[:];

y.nifi.undeploy.processGroups=[] as ArrayList;

y.nifi.undeploy.controllerServices=[] as ArrayList;

y.nifi.undeploy.templates=[t.name.text()]




/*Define Undeploy
---------------
*/
def cSerName =y.nifi.undeploy.controllerServices;
def PGList = y.nifi.undeploy.processGroups;


/*To Hold Undeploy structure
--------------------------*/

def xmlprocessgrouplist =[] as Set
def xmlcontrolSerlist = [] as Set
def nifiControlServices = [] as Set
def nifiprocessgrouplist  = [] as Set


/*Load all control services from  the NIFI rest url into a list
-------------------------------------------------------------
*/
if(node == "BOTH"||node.equals("BOTH")){
	loadControlServices(nifiControlServices,nifiapiurl,"NODE")
	loadControlServices(nifiControlServices,nifiapiurl,"NCM")
}else{

	loadControlServices(nifiControlServices,nifiapiurl,node)
}

//loadControlServices(nifiControlServices,nifiapiurl,node)


/*
Data structure to hold process groups and control services
----------------------------------------------------------
*/
y.processGroups = [:]
y.controllerServices = [:]
def yC = y.controllerServices


/*
 *   if the template xml file contains Control services ----> controllerServices.size()
 *   Add control services name and State to             ----> y.controllerServices
 *   if there are properties inside control services    ---->def xProps = xCs.properties?.entry
 *   For each control services check if the config name is found in the  control services MAP 
 * 	 Add the Control Service config name (From xml )  
 *   and Control service config value (from Properties file) to yml structure  
 */


if (t.snippet.controllerServices.size() > 0) {


	t.snippet.controllerServices.each { xCs ->

		yC[xCs.name.text()] = [:]
		yC[xCs.name.text()].state = 'ENABLED'
		xmlcontrolSerlist.add(xCs.name.text())
		def xProps = xCs.properties?.entry

		if (xProps.size() > 0) {


			yC[xCs.name.text()].config = [:]

			xProps.each { xProp ->


				if(controllerServicesMap.containsKey(xCs.name.text().toLowerCase().trim())){


					if(controllerServicesMap[xCs.name.text().toLowerCase().trim()].containsKey(xProp.key.text().toLowerCase().trim())){


						yC[xCs.name.text()].config[xProp.key.text()] = controllerServicesMap[xCs.name.text().toLowerCase().trim()].getAt(xProp.key.text().toLowerCase().trim())



					}

				}


			}

		}


	}
}


/*loads all process groups using NIFI rest api
---------------------------------------------
*/
loadprocessorGroups(nifiprocessgrouplist,nifiapiurl)


/*
if there are any process groups decide on group name
----------------------------------------------------
*/

if (t.snippet.processors.size() > 0) {

	parseGroup(processorGroupMap,t.snippet,y)

}

/*
for each process group call parse group
---------------------------------------
*/
t.snippet.processGroups.each {
	parseGroup(processorGroupMap,it,y)
	
	getXMLTemplateprocessGroup(xmlprocessgrouplist,it)
}


xmlcontrolSerlist.addAll(nifiControlServices)

xmlprocessgrouplist.addAll(nifiprocessgrouplist)

cSerName.addAll(xmlcontrolSerlist)
PGList.addAll(xmlprocessgrouplist)




/*Formatting
----------
*/

def yamlOpts = new DumperOptions()
yamlOpts.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
File file = new File(outputFile)
new Yaml(yamlOpts).dump(y,  new FileWriter(outputFile))
println("Successfully wrote YML file")
//........................End.......................................


/*
if no group name found return "root"
------------------------------------
*/

def static parseGroup(processorGroupMap,node,y) {

	def pgName = node?.name.text()
	

	if (!pgName) {
		pgName = 'root'
	}
		
	parseProcessors(processorGroupMap,pgName, null, node,y)
}


/*Get process group from xml template
------------------------------------
*/

def static getXMLTemplateprocessGroup(xmlprocessgrouplist,node){
	
	xmlprocessgrouplist.add(node.name.text())
	
}


/*Get Control Services from xml template
--------------------------------------
*/
def static getXMLTemplateControlService(xmlcontrolSerlist,node){

}


/*Get all process groups recursively
----------------------------------
*/
def static parseGrouprecursive(processorGroupMap,node,y,rootgroupName) {
	
		def pgName = node?.name.text()
		if (!pgName) {
			pgName = 'root'
		}
		parseProcessors(processorGroupMap,pgName, rootgroupName, node,y)
	}


/*Loads all control services in a list by REST API
------------------------------------------------
*/
def static loadControlServices(nifiControlServices,nifiapiurl,node){

	def resp = nifiapiurl.get(path: "controller/controller-services/"+node);
	assert resp.status == 200

	resp.data.controllerServices.each { pGs ->

		nifiControlServices.add(pGs.name)

	}
}


/*Loads all process groups in a list by REST API
----------------------------------------------
*/

def static loadprocessorGroups(nifiprocessgrouplist,nifiapiurl){

	def resp = nifiapiurl.get(path: "controller/process-groups/root/process-group-references");
	assert resp.status == 200

	resp.data.processGroups.each { pGs ->
		nifiprocessgrouplist.add(pGs.name)
	}

}


/*  For each process groups
 * 				Check the yml file and create the appropriate data structure to hold
 * 				process group -> process ->  Config Name : Config Value
 * 				if the process group- process - config name is found in the properties file
 * 							pull the value from properties file
 * `			else
 * 							add REPLACE ME
 */
def static parseProcessors(processorGroupMap,groupName, rootgroupName, node,y) {

	if(rootgroupName==null||rootgroupName.equals(null)){
		
		rootgroupName = groupName
		
	}
	
	def processors = node.contents.isEmpty() ? node.processors          // root process group
			: node.contents.processors 									// regular process group


	processors.each { p ->

		def ProcessName= p.name.text()

		
		p.config.properties?.entry?.each {
			
			if(processorGroupMap.containsKey(rootgroupName.toString().toLowerCase().trim())){

				def processorsMap = processorGroupMap[rootgroupName.toString().toLowerCase().trim()]

				if(processorsMap.containsKey(p.name.text().toLowerCase().trim())){

					def propertiesMap = processorsMap[p.name.text().toLowerCase().trim()]

					if(propertiesMap.containsKey(it.key.text().toLowerCase().trim())){
						

						if(!y.processGroups.containsKey(rootgroupName)){
							y.processGroups[rootgroupName] = [:]
							y.processGroups[rootgroupName].processors = [:]
							y.processGroups[rootgroupName].processors [p.name.text()] = [:]
							y.processGroups[rootgroupName].processors [p.name.text()].config = [:]
						}else{

							if(y.processGroups[rootgroupName].processors.containsKey(ProcessName)){

							}else{
								y.processGroups[rootgroupName].processors [p.name.text()] = [:]
								y.processGroups[rootgroupName].processors [p.name.text()].config = [:]

							}
						}

						//if (it.value.size() > 0) {

							//y.processGroups[rootgroupName].processors[p.name.text()].config[it.key.text()] = it.value.size() == 0 ? "REPLACEME" : propertiesMap.get(it.key.text().toLowerCase().trim());


							//y.processGroups[groupName].processors[p.name.text()].config[it.key.text()] = it.value.size() == 0 ? "No Value Found" : it.value.text()

						//}else{
							//def repl='REPLACEME'
							y.processGroups[rootgroupName].processors[p.name.text()].config[it.key.text()]=String.valueOf('REPLACEME')

						//}

					}else{
						
					}

				}else{

					
				}


			}else{
				
			}

		}
	}

		// Check For Process groups recursively
	 
		def subProcessGroups = node.contents.isEmpty() ?  node.processGroups : node.contents.processGroups

	

	if(! node.contents.processGroups.isEmpty()){


		node.contents.processGroups.each {
			
		it.contents.processors.each {
			   lt ->
			
			parseGrouprecursive(processorGroupMap,it,y,rootgroupName)
		
			}
			
		}
		
	}

}


/*
 * For each properties file line
 * 		if it has processgroups, processors and config
 * 				 Extract the data and populate in processgroup map			
 * 		if it has controllerServices and config
 * 				 Extract the data and populate in controllerServicesMap map	
 */

def static loadPropertiesMap(propertiesFile,processorGroupMap,controllerServicesMap){

	new File(propertiesFile).each { line ->
		def record = line.toString()

		if(record.contains("processgroups")&&record.contains("processors")&&record.contains("config")){

			def pGroup = record.substring(record.indexOf("processgroups."),record.indexOf(".processors.")).replaceAll("processgroups.", "")
			def processor =  record.substring(record.indexOf(".processors."),record.indexOf(".config.")).replaceAll(".processors.", "")
			def property = record.substring(record.indexOf(".config.")).replaceAll(".config.", "")
			def propertyName=	property.substring(0, property.toString().indexOf("="));
			def propertyValue = property.substring(property.toString().indexOf("=")).replaceAll("=", "")


			if(processorGroupMap.containsKey(pGroup)){


				if(processorGroupMap[pGroup].containsKey(processor)){

					processorGroupMap[pGroup][processor].putAt(propertyName, propertyValue)


				}else{
					//if Processor is not Found
					def propertyValMap= [:];
					propertyValMap.put(propertyName, propertyValue);
					processorGroupMap[pGroup].putAt(processor,propertyValMap)

				}}
			else{
				//If the entire Group is not there

				def propertyValMap= [:];
				def processorMap=[:];
				propertyValMap.put(propertyName, propertyValue);
				processorMap.put(processor, propertyValMap)
				processorGroupMap.put(pGroup, processorMap)


			}



		}else if(record.contains("controllerServices")&&record.contains("config")){

			
			def cServices=record.substring(record.indexOf("controllerServices."),record.indexOf(".config.")).replaceAll("controllerServices.", "")
			def config = record.substring(record.indexOf(".config.")).replaceAll(".config.", "")
			def configName=	config.substring(0, config.toString().indexOf("="));
			def configValue = config.substring(config.toString().indexOf("=")).replaceAll("=", "")

			def configValMap= [:];
			configValMap.putAt(configName,configValue)
			if(controllerServicesMap.containsKey(cServices)){



				controllerServicesMap[cServices].putAt(configName, configValue)


			}else{

				controllerServicesMap.put(cServices,configValMap)

			}

		}else{
			
		}


	}

}
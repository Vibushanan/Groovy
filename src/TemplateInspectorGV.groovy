import java.nio.file.Path
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions



class TemplateInspectorGV {

	def static templateUri="D:/new.xml";
	def static y = [:];
	def static t = new XmlSlurper().parse(templateUri)
	def static processorGroupMap = [:];
	def static processorMap= [:];

	static main(args) {
		loadPropertiesMap();
		mainFunction();
	}



	static void mainFunction(){


		y.nifi = [:]

		y.nifi.url= "http://localhost:8080/";

		y.nifi.clientId='REPLACEME';

		y.nifi.templateUri = templateUri

		y.nifi.templateName = t.name.text()

		y.nifi.gracefullShutDown=Boolean.TRUE;

		y.nifi.undeploy;

		y.undeploy=[:];

		y.undeploy.processGroups=[t.snippet.processGroups.size()];

		y.undeploy.controllerServices=[t.snippet.controllerServices.size()];

		y.undeploy.templates=[t.name.text()]

		def controlSerCount=0;
		def cSerName =y.undeploy.controllerServices;

		if (t.snippet.controllerServices.size() > 0) {

			t.snippet.controllerServices.each { xCs ->

				cSerName[controlSerCount]=xCs.name.text();

				y.controllerServices = [:]

				def yC = y.controllerServices

				yC[xCs.name.text()] = [:]
				yC[xCs.name.text()].state = 'ENABLED'

				//Check for property Entry

				def xProps = xCs.properties?.entry

				if (xProps.size() > 0) {

					yC[xCs.name.text()].config = [:]
					xProps.each { xProp ->

						if (xProp.value.size() > 0) {

							yC[xCs.name.text()].config[xProp.key.text()] = xProp.value.text()
						}
					}

				}


			}
		}



		y.processGroups = [:]


		if (t.snippet.processors.size() > 0) {

			parseGroup(t.snippet)

		}


		def PGList = y.undeploy.processGroups;

		def count = 0;


		t.snippet.processGroups.each {

			PGList[count]=it.name.text();

			count++
			parseGroup(it)
		}


		def yamlOpts = new DumperOptions()
		yamlOpts.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
		yamlOpts.prettyFlow = true


		println new Yaml(yamlOpts).dump(y)
	}


	def static parseGroup(node) {

		def pgName = node?.name.text()

		if (!pgName) {
			pgName = 'root'
		}



		parseProcessors(pgName, node)
	}




	def static parseProcessors(groupName, node) {


		def processors = node.contents.isEmpty() ? node.processors          // root process group
				: node.contents.processors // regular process group


		processors.each { p ->

			p.config.properties?.entry?.each {
				if(processorGroupMap.containsKey(groupName.toString().toLowerCase().trim())){

					def processorsMap = processorGroupMap[groupName.toString().toLowerCase().trim()]
					if(processorsMap.containsKey(p.name.text().toLowerCase().trim())){
						def propertiesMap = processorsMap[p.name.text().toLowerCase().trim()]
						if(propertiesMap.containsKey(it.key.text().toLowerCase().trim())){
							/*println("Succeeded..............   ")
							 println("in if has Value "+it.value.size());*/
							y.processGroups[groupName] = [:]
							y.processGroups[groupName].processors = [:]
							y.processGroups[groupName].processors[p.name.text()] = [:]
							y.processGroups[groupName].processors[p.name.text()].config = [:]
							def c = y.processGroups[groupName].processors[p.name.text()].config
							if (it.value.text() ==~ /[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}/) {
								def n = t.snippet.controllerServices.find { cs -> cs.id.text() == it.value.text() }

								/* if(n.isEmpty()){
								 c[it.key.text()] = '\${' + "EMpty"  + "}"
								 }*/
								assert !n.isEmpty() : "Couldn't resolve a Controller Service with ID: ${it.value.text()}"

								c[it.key.text()] = '\${' + n.name.text() + "}"
							}


							else  if (it.value.size() > 0) {

								// println("in else if has Value "+it.value.size());

								c[it.key.text()] = it.value.size() == 0 ? null : it.value.text()
							}


						}else{
							/*println("Not in Property  ")
							 println("Failed  Group :"+groupName.toString().toLowerCase().trim()+" Processor  :"+p.name.text()
							 +"   Property   :"+it.key.text());*/
						}

					}else{

						/*println("Not in processors  ")
						 println("Failed  Group :"+groupName.toString().toLowerCase().trim()+" Processor  :"+p.name.text()
						 +"   Property   :"+it.key.text());*/
					}


				}else{
					/*println("Not in Group  ")
					 println("Failed  Group :"+groupName.toString().toLowerCase().trim()+" Processor  :"+p.name.text()
					 +"   Property   :"+it.key.text());*/
				}


				// check if it's a UUID and try lookup the CS to get the name
				//println(c)



			}
		}
	}

	def static loadPropertiesMap(){

		new File("D:\\property_file.txt").each { line ->
			def record = line.toString()

			def pGroup = record.substring(record.indexOf("processgroups."),record.indexOf(".processors.")).replaceAll("processgroups.", "")
			def processor =  record.substring(record.indexOf(".processors."),record.indexOf(".config.")).replaceAll(".processors.", "")
			def property = record.substring(record.indexOf(".config.")).replaceAll(".config.", "")
			def propertyName=	property.substring(0, property.toString().indexOf("="));
			def propertyValue = property.substring(property.toString().indexOf("=")).replaceAll("=", "")
			if(processorMap.containsKey(processor)){
				processorMap[processor].putAt(propertyName, propertyValue)
			}else{
				def propertyValMap= [:];
				propertyValMap.put(propertyName, propertyValue);
				processorMap.put(processor, propertyValMap)
			}
			if(processorGroupMap.containsKey(pGroup)){
				processorGroupMap[pGroup].putAt(pGroup, processorMap)
			}else{
				processorGroupMap.put(pGroup, processorMap)
			}
		}
		println("-----------Property Map Loaded--------------")
	}

}

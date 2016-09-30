import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml


class Sample {

	static void main(String[] args) {
		
		def y=[:]
		
		y.nifi=[:]
		
		def List<String> st =["hello","hi"]
		
		def li=[];
		li[0]="ddsd"
		
		li[1]=st
		
		
	  y.nifi.undeploy=[:]
	  
	   y.nifi.undeploy.process=[:]
	   
	  y.nifi.undeploy.process.putAt("Prr",li) 	  
	  
	  
	  def yamlOpts = new DumperOptions()
	  yamlOpts.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
	  println new Yaml(yamlOpts).dump(y)
		
	}
}

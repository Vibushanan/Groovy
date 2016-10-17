import groovy.json.JsonBuilder
import groovyx.net.http.RESTClient
import static groovyx.net.http.ContentType.URLENC

def nifiapiurl ;
def currentRevision;

nifiapiurl = new RESTClient("http://localhost:8080/nifi-api/");

def resp = nifiapiurl.get(path: "controller/controller-services/NODE")

//println resp.data;



/*def response = nifiapiurl.get(
	path: 'controller/revision'
)
assert response.status == 200

println response.data

currentRevision = response.data.revision.version
*/





/*def response1 = nifiapiurl.put (
	path: "controller/controller-services/node/a39b5847-67d1-4869-a3c1-5564cdb76a02/references",
	body: [
	  clientId: "Vibushanan",
	  version: currentRevision,
	  state: 'STOPPED'
	],
	requestContentType: URLENC
  )

println response1.data*/


def response3 = nifiapiurl.get(
	path: 'controller/revision'
)
assert response3.status == 200


currentRevision = response3.data.revision.version

println currentRevision

def resp4 = nifiapiurl.delete(
	path: "controller/controller-services/NODE/a39b5847-67d1-4869-a3c1-5564cdb76a02",
	query: [
	  clientId: "Vibushanan",
	  version: currentRevision
	]
  )
assert resp4.status == 200

println resp4.data
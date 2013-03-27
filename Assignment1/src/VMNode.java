
public class VMNode {
	private String instanceId;
	private String elasticIp;
	private String amiId;
	private String volumeId;
	
	//Getters
	public String getInstanceid() {
		return this.instanceId;
	}
	
	public String getElasticIp() {
		return this.elasticIp;
	}
	
	public String getAmiId() {
		return this.amiId;
	}
	
	public String getVolumeId() {
		return this.volumeId;
	}
	
	//Setters
	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}
	
	public void setElasticIp(String elasticIp) {
		this.elasticIp = elasticIp;
	}
	
	public void setAmiId(String amiId) {
		this.amiId = amiId;
	}
	
	public void setVolumeId(String volumeId) {
		this.volumeId = volumeId;
	}
}



import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

import com.amazonaws.services.ec2.model.DeregisterImageRequest;
import com.amazonaws.services.ec2.model.Instance;

public class Task extends TimerTask{
	private String objectName;
	
	Task(String objectName) {
		this.objectName = objectName;
	}
	
	@Override
	public void run() {
		Date date = new Date();
		SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy hh:mm:ss");
		String current_time = format.format(date);

		// Output to user the name of the objecet and the current time
		System.out.println(objectName + " - Current time: " + current_time);
		if(MonitorVMs.timerCounter == 5) {//sleep mode
			//Go to active mode
			System.out.println("Calling create VMs");
			for(VMNode n : MonitorVMs.nodes) {
				//Create Instance
				Instance ins = MonitorVMs.restoreInstance(n.getAmiId());
				n.setInstanceId(ins.getInstanceId());
				while(true) {
					ins = MonitorVMs.updatedInstance(ins);
					//if(ins.getPublicIpAddress()!= null && ins.getState().getName().equals("running")){
					if(ins.getState().getName().equals("running")){
                        break;
                    }
				}
				String instanceId = ins.getInstanceId();
				System.out.println("New instance : " + instanceId + " is : " + ins.getState().getName());
				System.out.println("New instance : " + instanceId + " created from AMI: " + ins.getImageId());
				//Associate Elastic IP
				String elasticIp = n.getElasticIp();
				MonitorVMs.associateElasticIP(instanceId, elasticIp);
				//Attach Volume
				String volumeId = n.getVolumeId();
				MonitorVMs.attachVolume(instanceId, volumeId);
				//Deregistering AMI
				DeregisterImageRequest dir = new DeregisterImageRequest();
				dir.setImageId(n.getAmiId());
				MonitorVMs.ec2.deregisterImage(dir);
			}
			MonitorVMs.updateCounter();
		}
		else if(MonitorVMs.timerCounter == 9) {//active mode
			//Go to sleep mode
			System.out.println("Shutting down VMs");
			
			//Extract list of currently running instances
			List<Instance> runningInstanceList = MonitorVMs.getInstanceList();
			
			for(Instance ins : runningInstanceList) {
				for(VMNode n : MonitorVMs.nodes) {
					if(n.getElasticIp().equals(ins.getPublicIpAddress())) {
						//Detach Volume
						String instanceId = n.getInstanceid();
						String volumeId = n.getVolumeId();
						MonitorVMs.detachVolume(instanceId, volumeId);
						//Create AMI from instance
						String imageId = MonitorVMs.createAmi(instanceId);
						n.setAmiId(imageId);
						System.out.println("New AMI : " + imageId + "created from instance : " + instanceId);
						//Terminate instance
						MonitorVMs.terminateInstance(instanceId);
					}
				}
			}
			MonitorVMs.updateCounter();
		}
	}

}

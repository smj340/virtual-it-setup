import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;



import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.autoscaling.model.Ebs;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AllocateAddressResult;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.AttachVolumeRequest;
import com.amazonaws.services.ec2.model.CreateImageRequest;
import com.amazonaws.services.ec2.model.CreateImageResult;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreateVolumeRequest;
import com.amazonaws.services.ec2.model.CreateVolumeResult;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DetachVolumeRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateKeyPairResult;
import com.amazonaws.services.ec2.model.CreateKeyPairRequest;
import com.amazonaws.services.ec2.model.KeyPair;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.sun.java.util.jar.pack.*;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.autoscaling.model.Ebs;


import java.util.ArrayList;
import java.util.Scanner;

public class AwsSample {

/*
* Important: Be sure to fill in your AWS access credentials in the
*            AwsCredentials.properties file before you try to run this
*            sample.
* http://aws.amazon.com/security-credentials
*/

static AmazonEC2 ec2;


public static void main(String[] args) throws Exception {

AWSCredentials credentials = new PropertiesCredentials(
AwsSample.class.getResourceAsStream("AwsCredentials.properties"));



/*********************************************
*
*  #1 Create Amazon Client object
*
*********************************************/
System.out.println("#1 Create Amazon Client object");
ec2 = new AmazonEC2Client(credentials);

String createdImageId=null;
String keyofAmi=null;


try {

/*********************************************
*
*  #6 Create an Instance
*
*********************************************/
System.out.println("#5 Create an Instance");
String imageId = "ami-76f0061f"; //Basic 32-bit Amazon Linux AMI
int minInstanceCount = 1; // create 1 instance
int maxInstanceCount = 1;
RunInstancesRequest rir = new RunInstancesRequest(imageId,
minInstanceCount, maxInstanceCount);

rir.setKeyName("testElastic");


rir.withSecurityGroups("Prachi");


RunInstancesResult result = ec2.runInstances(rir);


//get instanceId from the result
List<Instance> resultInstance = result.getReservation().getInstances();
Instance runningInstance = null;
String createdInstanceId = null;
String zone=null;


for (Instance insn : resultInstance)
{

while (true)
{
Thread.sleep(1000);
System.out.print(insn.getState().getName()+"\n");

insn = getUpdatedInstance(insn);
createdInstanceId = insn.getInstanceId();
runningInstance = insn;
zone=insn.getPlacement().getAvailabilityZone();
if(insn.getPublicIpAddress()!= null){
break;
}

}

System.out.println("Instance is now running");
System.out.println("State: " + insn.getState().getName());

System.out.println("Public DNS Name: " + insn.getPublicDnsName());
System.out.println("Public DNS IP Address: " + insn.getPublicIpAddress());

/*********************************************
*  	Allocate elastic IP addresses.
*********************************************/
//allocate
AllocateAddressResult elasticResult = ec2.allocateAddress();
String elasticIp = elasticResult.getPublicIp();
System.out.println("New elastic IP: "+elasticIp);
//associate
AssociateAddressRequest aar = new AssociateAddressRequest();
aar.setInstanceId(createdInstanceId);
aar.setPublicIp(elasticIp);
ec2.associateAddress(aar);
//disassociate
DisassociateAddressRequest dar = new DisassociateAddressRequest();
dar.setPublicIp(elasticIp);
ec2.disassociateAddress(dar);
			

}


/*********************************************
*
*  #7 Create a 'tag' for the new instance.
*
*********************************************/
System.out.println("#6 Create a 'tag' for the new instance.");
List<String> resources = new LinkedList<String>();
List<Tag> tags = new LinkedList<Tag>();
Tag nameTag = new Tag("Name", "MyFirstInstance");

resources.add(createdInstanceId);
tags.add(nameTag);

CreateTagsRequest ctr = new CreateTagsRequest(resources, tags);
ec2.createTags(ctr);

/*********************************************
*  #2.1 Create a volume
*********************************************/
//create a volume
CreateVolumeRequest cvr = new CreateVolumeRequest();


cvr.setAvailabilityZone(zone);
cvr.setSize(10); //size = 10 gigabytes
CreateVolumeResult volumeResult = ec2.createVolume(cvr);
String createdVolumeId = volumeResult.getVolume().getVolumeId();


/*********************************************
*  #2.2 Attach the volume to the instance
*********************************************/
AttachVolumeRequest avr = new AttachVolumeRequest();
avr.setVolumeId(createdVolumeId);
avr.setInstanceId(createdInstanceId);
avr.setDevice("/dev/sdf");
ec2.attachVolume(avr);

/*********************************************
*  #2.3 Detach the volume from the instance
*********************************************/
DetachVolumeRequest dvr = new DetachVolumeRequest();
dvr.setVolumeId(createdVolumeId);
dvr.setInstanceId(createdInstanceId);
ec2.detachVolume(dvr);



/***********************************
*   #8 Create an AMI from an instance
*********************************/
CreateImageRequest cir = new CreateImageRequest();
cir.setInstanceId(createdInstanceId);
cir.setName("hw_test_ami");
CreateImageResult createImageResult = ec2.createImage(cir);
createdImageId = createImageResult.getImageId();

System.out.println("Image Id is:"+createdImageId);
keyofAmi="hw_test_ami";


System.out.println("Sent creating AMI request. AMI id="+createdImageId);


        /****************************************************************
         * Storing in S3
         * **************************************************************
         */


AmazonS3Client s3  = new AmazonS3Client(credentials);

//create bucket
String bucketName = "nomenclature-sakes";
s3.createBucket(bucketName);

//set key
String key = "object-name.txt";

//set value
File file = File.createTempFile("temp", ".txt");
file.deleteOnExit();
Writer writer = new OutputStreamWriter(new FileOutputStream(file));
writer.write("This is a sample sentence.\r\nYes!");
writer.close();

File imagefile = File.createTempFile("imageID", ".txt");
imagefile.deleteOnExit();
Writer imagewriter = new OutputStreamWriter(new FileOutputStream(imagefile));
imagewriter.write(createdImageId);
imagewriter.close();

//put object - bucket, key, value(file)
s3.putObject(new PutObjectRequest(bucketName, key, file));
s3.putObject(new PutObjectRequest(bucketName,keyofAmi,imagefile));

//get object
S3Object object = s3.getObject(new GetObjectRequest(bucketName, key));
BufferedReader reader = new BufferedReader(
new InputStreamReader(object.getObjectContent()));
String data = null;
while ((data = reader.readLine()) != null) {
System.out.println(data);
}



/*********************************************
*  #4 shutdown client object
*********************************************/

s3.shutdown();

/*********************************************
*
*  #9 Stop/Start an Instance
*
*********************************************/

List<String> instanceIds = new ArrayList<String>();
instanceIds.add(runningInstance.getInstanceId());
/*********************************************
*
*  #9 Terminate an Instance
*
*********************************************/

TerminateInstancesRequest tir = new TerminateInstancesRequest(instanceIds);
ec2.terminateInstances(tir);

/********************************************
*  #6 Create an Instance again from AMI
*
*********************************************/
System.out.println("#5 Create an Instance");
imageId = createdImageId; //Basic 32-bit Amazon Linux AMI
   minInstanceCount = 1; // create 1 instance
maxInstanceCount = 1;
rir = new RunInstancesRequest(imageId,
minInstanceCount, maxInstanceCount);

rir.setKeyName("ShwetaKeyPair");
rir.withSecurityGroups("Shweta");
result = ec2.runInstances(rir);


} catch (AmazonServiceException ase) {
System.out.println("Caught Exception: " + ase.getMessage());
System.out.println("Reponse Status Code: " + ase.getStatusCode());
System.out.println("Error Code: " + ase.getErrorCode());
System.out.println("Request ID: " + ase.getRequestId());
}










}


public static Instance getUpdatedInstance(Instance ins) {

int i,j;
List<Reservation> reserveList = ec2.describeInstances().getReservations();

for(i =0 ; i<reserveList.size();i++){
List<Instance> instanceList = reserveList.get(i).getInstances();
for(j = 0;j <instanceList.size();j++){
if(instanceList.get(j).getInstanceId().equalsIgnoreCase(ins.getInstanceId())){
return instanceList.get(j);
}
}
}


return null;
}
}
			
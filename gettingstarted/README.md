## Summary
Edge Connector For Kinesis Video Stream component stores its configuration in AWS IoT SiteWise, which is an AWS service that models and stores industrial data. In AWS IoT SiteWise, assets represent objects such as devices, equipment, or groups of other objects. 

This script will help you the needed AWS resources to use Edge Connector For Kinesis Video Stream. Use this script, you can create one or more AWS IoT SiteWise asset for each Greengrass core device (the hub) and for each IP camera connected to each core device which been managed in the hub's network. Each camera SiteWise asset has properties that you configure to control features such as live streaming, on-demand upload, and local caching. 

This script will help you generate you needed SiteWise, Kinesis Video Stream. This script will also help you to specify the URL for each camera, and create a secret in AWS Secrets Manager that contains the URL of the camera, including a username and password if the camera requires authentication. 

Note: These instructions have primarily been tested for Mac/Linux environments.

## Prerequisites
- An AWS account
- Install [Python3](https://www.python.org/downloads/)
- Install [boto3](https://boto3.amazonaws.com/v1/documentation/api/latest/guide/quickstart.html)
  - Quick install: ```pip install boto3```
  - Have [AWS CLI](https://aws.amazon.com/cli/) installed, use ```aws configure``` command to configure your credentials file
  - This script needs the following minimum IAM permission policies:
     - kinesisvideo:CreateStream
     - kinesisvideo:ListStreams
     - secretsmanager:CreateSecret
     - iotsitewise:AssociateAssets     
     - iotsitewise:BatchPutAssetPropertyValue
     - iotsitewise:CreateAsset
     - iotsitewise:CreateAssetModel
     - iotsitewise:DescribeAsset
     - iotsitewise:DescribeAssetModel          
     - iotsitewise:ListAssets
     - iotsitewise:ListAssetModels     
     - iotsitewise:UpdateAssetProperty     
- If you want to update the role in the AWS IAM console, use the following JSON: 
  ```{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "iot:DescribeCertificate",
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams",
        "s3:GetBucketLocation",
        "s3:GetObject",
        "secretsmanager:GetSecretValue",
        "iotsitewise:BatchPutAssetPropertyValue",
        "iotsitewise:DescribeAsset",
        "iotsitewise:DescribeAssetModel",
        "iotsitewise:ListAssetRelationships",
        "iotsitewise:ListAssets",
        "iotsitewise:ListAssociatedAssets",
        "iotsitewise:DescribeAssetProperty",
        "iotsitewise:GetAssetPropertyValue",
        "kinesisvideo:DescribeStream",
        "kinesisvideo:PutMedia",
        "kinesisvideo:TagStream",
        "kinesisvideo:GetDataEndpoint",
        "kinesisvideo:CreateStream"
            ].
      "Resource": "*"
    }
  ]
   }

- Install [pyyaml](https://github.com/yaml/pyyaml)
  - Quick install, thype this into the command line: ```pip install pyyaml```   

## How to use
Step 1. Edit resource_configure.yml
  - Follow the instructions in the file to add your needed hub and camera setting
  - Each hub may manage multiple cameras. You can create multiple camera assets then add their "Name" (which will be the SiteWise asset name for this camera) into hub's "ChildrenCameraSiteWiseAssetName" array. In this way the script will create needed assets and do the association.  
   
Step 2. run ```python ./resourceManager.py```

Step 3. After execute, script will generated following resources:
  - SiteWise asset model for hubs and cameras
  - SiteWise assets for hubs and cameras
  - If configured Kinesis Video Stream name does not exist, create the stream
  - Secret ARN to store camera's RTSP url.
## Configure SiteWise Assets
**Note:** Many of the camera model attributes use Cron time formatted strings `* * * * *`. For more information on using this format see the [UNIX cron format](https://www.ibm.com/docs/en/db2oc?topic=task-unix-cron-forma). Use all dashes `-` in an express to signal never, and use all stars `*` to singal always in an attribute. Each attribute is defined as follows:
  - **KinesisVideoStreamName:** The name of your video stream. You use this name in your Grafana dashboard video panel, to stream in video in Grafan.
  - **RTSPStreamSecretARN:** Many cameras with streaming capabilities, use a login sytem with a username and password as a security measure. This ARN can be updated in &ASM; to to store the cameras login information. For steps on editing the secert ARN in Amazon Secerts Manager, see https://docs.aws.amazon.com//secretsmanager/latest/userguide/intro.html#asm_access" Access Secrets Manager.
  - **LocalDataRetentionPeriodInMinutes:** How long the stream data is retained on the device. The unit is measured in minutes.
  - **LiveStreamingStartTime:** The time the componenet will start sending data from your device to Amazon Kensis video streams.
  - **LiveStreamingDurationInMinutes:** The total time a camera will send video to Amazon Kensis video streams. 
  - **CaptureStartTime:** The start time of the local recording.
  - **CaptureDurationInMinutes:** The total time the device will record video locally.
**Note:** The **LiveStreamingDurationInMinutes** and **LiveStreamingStartTime** cannot be more frequent that the **CaptureDurationInMinutes** and the **CaptureStartTime**.


After these steps, please go to AWS SiteWise console and check the new created SiteWise asset for hub device. Using it's asset Id to configure Edge connector for Kinesis Video Streams and finish the deployment.

## Notes
After creation, please remove the content in the resource_configure.yml to preventing information leaking.

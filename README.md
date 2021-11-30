# AWS IoT Greengrass Edge Connector for Kinesis Video Streams

AWS IoT Greengrass Edge Connector for Kinesis Video Streams is a reference design of a Greengrass v2 Component for ingesting video from the edge to AWS Kinesis Video Streams. The Component connects to IP Cameras within the same network, reads the feed via RTSP and uploads the video fragments to [AWS Kinesis Video Streams](https://aws.amazon.com/kinesis/video-streams/). This component supports features like Edge Video Caching, Scheduled Video Recording, Scheduled Video Uploading, Live Streaming and Historical Video Uploading via MQTT.

This component stores its configuration in AWS IoT SiteWise, which is an AWS service that models and stores industrial data. To configure and use this component, you create an AWS IoT SiteWise asset for each Greengrass core device and for each IP camera connected to the core device. Each asset has properties that you configure to control features, such as live streaming, on-demand upload, and local caching. To specify the URL for each camera, you create a secret in AWS Secrets Manager that contains the URL of the camera. If the camera requires authentication, you also specify a user name and password in the URL. Then, you specify that secret in an asset property for the IP camera.

The goal of this repository is to provide a fully working Greengrass V2 Component for Video Ingestion which can be customized as per customers needs.

## Table of Contents
* [Architecture](#architecture)
* [Repository Contents](#repository-contents)
* [Requirements and Prerequisites](#requirements-and-prerequisites)
  * [Greengrass Core Device](#greengrass-core-device)
    * [Platform](#platform)
    * [Edge Runtime](#edge-runtime)
    * [GStreamer](#gstreamer)
  * [Greengrass Cloud Services](#greengrass-cloud-services)
    * [Core Device Role](#core-device-role)
* [Getting Started](#getting-started)
* [Component Configuration](#component-configuration)
* [Development](#development)


### Architecture

An overview of system architecture is show below

![ggv2-edgeconnectorforkvs-architecture](https://user-images.githubusercontent.com/765100/144143289-2dca027b-73cf-4f29-b787-ce7cc9efa7ad.png)

### Repository Contents


| Item                          | Description                                                                                           |
| ----------------------------- | ----------------------------------------------------------------------------------------------------- |
| /src/main                     | Source code for the Edge Connector for KVS Component.                                                 |
| /src/test                     | Unit Tests for the Edge Connector for KVS Component.                                                  |
| /gettingstarted               | Helper script to bootstrap resources required by this Component in an AWS account.                    |
| /libs                         | Shared Java libraries.                                                                                |
| /recipe                       | Greengrass V2 component recipe template.                                                              |
| /docs                         | Documentation for Edge Connector for KVS Component.                                                   |

## Requirements and Prerequisites

### Greengrass Core Device

#### Platform

This component requires that the Greengrass device be running a Linux operating system. It [supports all architectures supported by Greengrass](https://docs.aws.amazon.com/greengrass/v2/developerguide/setting-up.html#greengrass-v2-supported-platforms).

#### Edge Runtime

The [Greengrass edge runtime needs to be deployed](https://docs.aws.amazon.com/greengrass/v2/developerguide/getting-started.html) to a suitable machine, virtual machine or EC2 instance

#### GStreamer

This component uses the [GStreamer framework](https://gstreamer.freedesktop.org/) to read video feed from IP Cameras. Please follow the [installation guide](https://gstreamer.freedesktop.org/documentation/installing/index.html?gi-language=c) to install GStreamer in your environment.

### Greengrass Cloud Services

#### Core Device Role

This component uses AWS IoT SiteWise to store the configuration and also to push the metadata related to video uploads. Therefore, your Greengrass core device role must allow various **iotsitewise:xxxx** permissions.

Additionally, this component reads sensitive IP Camera URL with credentials from Secrets Manager. Therefore, your Greengrass core device role must also allow the **secretsmanager:GetSecretValue** permission for each of the **IPCamera** secrets. 

Moreover, this component pushes the video fragments to AWS Kinesis Video Streams. Therefore, your Greengrass core device role must also allow various **kinesisvideo:yyyy** permissions. 

You must update the policy for the Greengrass TokenExchangeRole in the IAM console. This role is created when you install the Greengrass core device software. Update the role's policy, by replacing it with the following policy (substitute correct values for REGION, ACCOUNT_ID and SECRET_ID):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Effect": "Allow",
      "Resource": [
        "arn:aws:secretsmanager:REGION:ACCOUNT_ID:secret:SECRET_ID1",
        "arn:aws:secretsmanager:REGION:ACCOUNT_ID:secret:SECRET_ID2"
      ]
    },
    {
      "Action": [
        "iotsitewise:BatchPutAssetPropertyValue",
        "iotsitewise:DescribeAsset",
        "iotsitewise:DescribeAssetModel",
        "iotsitewise:DescribeAssetProperty",
        "iotsitewise:GetAssetPropertyValue",
        "iotsitewise:ListAssetRelationships",
        "iotsitewise:ListAssets",
        "iotsitewise:ListAssociatedAssets",
        "kinesisvideo:CreateStream",
        "kinesisvideo:DescribeStream",
        "kinesisvideo:GetDataEndpoint",
        "kinesisvideo:PutMedia",
        "kinesisvideo:TagStream"
      ],
      "Effect": "Allow",
      "Resource": [
        "*"
      ]
    }
  ]
}
```

## Getting Started

Please follow the [Getting Started guide](gettingstarted/README.md) to bootstrap resources like SiteWise Models, Assets and Secrets in your AWS account which are used by the Edge Connector for KVS Component. Then follow the below steps to deploy the EdgeConnectorForKVS component on your Greengrass core device

1. Sign in to the AWS Greengrass Console. In the navigation menu, choose **Components**.
2. On the **Components** page, on the **Public components** tab, select <code>aws.iot.EdgeConnectorForKVS</code>.
3. On the **aws.iot.EdgeConnectorForKVS** page, choose **Deploy**.
4. On the **Add to deployment** popup, select an existing deployment group that was created when you installed the Greeengrass core software (Nucleus), then  choose **Next**.
5. On the **Specify target**, you do not need to update any options, simply choose **Next**.
6. On the **Select components** page, verify that the **aws.iot.EdgeConnectorForKVS** component is selected, and choose **Next**.
7. On the **Configure components** page, select the **aws.iot.EdgeConnectorForKVS**, and choose **Configure component** from the top right of the menu. This will open the **Configuration update** page.
8. On the **Configuration update** page, under **Configuration to merge** section, replace the JSON with the AWS SiteWise _Asset Id_ for the EdgeConnectorForKVSHub asset that was created when you ran the GettingStarted script. This _Asset Id_ can also be found in the AWS SiteWise Console, under the **Assets** menu. Copy the Asset Id of the created EdgeConnectorForKVSHub asset (eg: **EdgeConnectorForKVSHub-0aaa1234a111**). An example of the merge configuration is as follows: 
```json
{
    "SiteWiseAssetIdForHub": "your-hub-asset-here"
}
```
9. On the **Configure advanced setting** page, keep the default configuration settings, and choose **Next**.
10. On the **Review** page, click **Deploy** to finish the deployment.
11. Once the Component is deployed to the Greengrass core device, you can look at the `aws.iot.EdgeConnectorForKVS.log` file in your Greengrass core logs directory (`/greengrass/v2/logs`) for any errors.
12. If you decide to change any EdgeConnectorForKVS configuration in SiteWise assets, please use `greengrass-cli` to restart the component.
```sh
/greengrass/v2/bin/greengrass-cli component restart --names "aws.iot.EdgeConnectorForKVS"
```

## Component Configuration

This component stores its configuration in the SiteWise asset properties. The SiteWise assets should follow the hierarchy as below:

```text
EdgeConnectorForKVSHub-123abc
├── EdgeConnectorForKVSCamera-456def
├── EdgeConnectorForKVSCamera-789abc
├── ..
├── ..
```

Please refer to the [Getting Started](#getting-started) section to set this up in your AWS account.

The `EdgeConnectorForKVSHub` asset has a single string asset attribute called `Hub Name` which can be set to any valid string.

The `EdgeConnectorForKVSCamera` asset has below set of properties 

| Property Name                      | Type       | Description                                                                                 |
| ---------------------------------- |----------- | ------------------------------------------------------------------------------------------- |
| KinesisVideoStreamName             | Attribute  | KVS Stream name where video from this Camera will be uploaded to.                           |
| RTSPStreamSecretARN                | Attribute  | ARN to Camera Secret that stores the RTSP URL for this Camera.                              |
| LocalDataRetentionPeriodInMinutes  | Attribute  | Defines how much video data in minutes should be cached on the edge.                        |
| LiveStreamingStartTime             | Attribute  | Cron expression which defines when the live streaming for this Camera should be triggered.  |
| LiveStreamingDurationInMinutes     | Attribute  | Once triggered, how many minutes should the live streaming continue.                        |
| CaptureStartTime                   | Attribute  | Cron expression which defines when the recording for this Camera should be triggered.       |
| CaptureDurationInMinutes           | Attribute  | Once triggered, how many minutes should the recording continue.                             |


### Cron Expression format

The `LiveStreamingStartTime` and `CaptureStartTime` properties follow a Cron expression format. A cron expression is a string comprised of 5 or 6 fields separated by white space in the form of `Minutes | Hours | Day of Month | Month | Day of Week`. A single dash `-` is used to represent `never`, where as all asterisks `* * * * *` is used to represent always.

| Field Name    | Required           |  Allowed Values   | Allowed Special Characters |
| ------------- | ------------------ | ------------------| -------------------------- |
| Minutes       |   Yes              |      0-59         |  , - * /                   |
| Hours         |   Yes              |      0-23         |  , - * /                   |
| Day of Month  |   Yes              |      1-31         |  , - * / ?                 |
| Month         |   Yes              | 1-12 or JAN-DEC   |  , - * /                   |
| Day of Week   |   Yes              | 1-7 or SUN-SAT    |  , - * /                   |
| Year          |   No               | Empty, 1970-2099  |  , - * /                   |

`*` ("all values") - used to select all values within a field. For example, "*" in the minute field means _every minute_.

`?` ("no specific value") - useful when you need to specify something in one of the two fields in which the character is allowed, but not the other. For example, if I want my trigger to fire on a particular day of the month (say, the 10th), but don’t care what day of the week that happens to be, I would put "10" in the day-of-month field, and "?" in the day-of-week field. See the examples below for clarification.

`-` - used to specify ranges. For example, "10-12" in the hour field means _the hours 10, 11 and 12_.

`,` - used to specify additional values. For example, "MON,WED,FRI" in the day-of-week field means _the days Monday, Wednesday, and Friday_.

`/` - used to specify increments. For example, "0/15" in the seconds field means _the seconds 0, 15, 30, and 45_. And "5/15" in the seconds field means _the seconds 5, 20, 35, and 50_. "1/3" in the day-of-month field means _fire every 3 days starting on the first day of the month_.

**NOTE**: Support for specifying both a day-of-week and a day-of-month value is not currently allowed (you must currently use the `?` character in one of these fields).

#### Examples

| Expression   | Meaning                             |
| ------------ | ----------------------------------- |
| 0 12 * * ?   | Start at 12pm (noon) every day      |
| 15 10 ? * *  | Start at 10:15am  every day         |
| 0/5 * * * ?  | Start every 5mins                   |
| 0 */1 * * ?  | Start every hour                    |
| -            | Never start                         |
| * * * * *    | Start immediately and never stop    |

## Development

The component uses gradle build system. So if you decide to make any code changes, the component can be built using the below command 

```
./gradlew clean build
```

This should create a Super JAR file (JAR file with all the library dependencies bundled together) in the `/output` directory. To deploy this locally generated component, you can follow this [Greengrass guide for local deployments](https://docs.aws.amazon.com/greengrass/v2/developerguide/test-components.html) while using the provided [recipe](recipe/aws.iot.edgeconnectorforkvs.yaml) file as template.

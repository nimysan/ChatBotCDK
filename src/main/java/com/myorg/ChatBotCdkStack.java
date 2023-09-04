package com.myorg;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.rds.InstanceProps;
import software.constructs.Construct;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.IConstruct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class ChatBotCdkStack extends Stack {

    private final static int DB_PORT = 5432;

    public ChatBotCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public ChatBotCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // example resource
        // final Queue queue = Queue.Builder.create(this, "ChatBotCdkQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();

        // 创建一个输入参数对象
//        CfnParameter vpcIdParameter = CfnParameter.Builder.create(this, "VpcId")
//                .type("String")
//                .description("VPC ID")
//                .build();
        // key pair name
        CfnParameter ec2KeyPairName = CfnParameter.Builder.create(this, "ec2KeyPair")
                .type("String")
                .description("EC2 Key Pair name")
                .build();
//
//
//        // key pair name
//        CfnParameter instanceTypeParameter = CfnParameter.Builder.create(this, "instanceType")
//                .type("String")
//                .description("instanceTypeParameter")
//                .build();
        IVpc vpc = Vpc.fromLookup(this, "VPC", VpcLookupOptions.builder()
                // This imports the default VPC but you can also
                // specify a 'vpcName' or 'tags'.
                .vpcId("vpc-08cd91d07ef1cfbfa")
                .isDefault(true)
                .build());
//
//
//
        SecurityGroup chatBotSecurityGroup = SecurityGroup.Builder.create(this, "ChatBotSecurityGroup")
                .vpc(vpc)
                .description("Allow ssh access to ec2 instances")
                .allowAllOutbound(true)
                .build();
        chatBotSecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(DB_PORT), "allow ssh access from the world");


        SecurityGroup chatBotRDSSecurityGroup = SecurityGroup.Builder.create(this, "ChatBotRDSSecurityGroup")
                .vpc(vpc)
                .description("Allow EC2 to connect pg")
                .allowAllOutbound(true)
                .build();
        chatBotRDSSecurityGroup.addIngressRule(Peer.securityGroupId(chatBotSecurityGroup.getSecurityGroupId()), Port.tcp(5432), "allow web service to access postgresql");
//        //TODO 连接数据库所在
//
//        //创建EC2
        Instance.Builder.create(this, "webServer")
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.C5, InstanceSize.LARGE))
                .machineImage(MachineImage.latestAmazonLinux2023())
                .securityGroup(chatBotSecurityGroup)
                .keyName(ec2KeyPairName.getValueAsString())
                .build();

        // create database
        final DatabaseSecret databaseSecret = new DatabaseSecret(this, "dbSecret", DatabaseSecretProps.builder()
                .username("dbUser")
                .secretName("dbPassword")
                .build());

        DatabaseCluster serverlessCluster = getDatabaseCluster(databaseSecret, vpc, chatBotRDSSecurityGroup);

        //output
        CfnOutput.Builder.create(this, "database-arn")
                .value(serverlessCluster.getClusterIdentifier())
                .build();

//        CfnOutput.Builder.create(this, "database-arn")
//                .value(d)
//                .build();
    }

    /**
     * <pre>
     *     this.serverlessCluster = new DatabaseCluster(
     *             this,
     *             'ServerlessClusterV2',
     *             {
     *                 engine: DatabaseClusterEngine.auroraMysql({
     *                     version: AuroraMysqlEngineVersion.of(
     *                         '8.0.mysql_aurora.3.02.0'
     *                     ), // The new minor version of Database Engine.
     *                 }),
     *                 storageEncrypted: true,
     *                 iamAuthentication: true,
     *                 parameterGroup: ParameterGroup.fromParameterGroupName(
     *                     this,
     *                     'rdsClusterPrameterGroup',
     *                     'default.aurora-mysql8.0'
     *                 ),
     *                 storageEncryptionKey: new Key(this, 'dbEncryptionKey'),
     *                 instanceProps: {
     *                     instanceType:
     *                         CustomInstanceType.SERVERLESS as unknown as InstanceType,
     *                     vpc: props.vpc,
     *                     vpcSubnets: {
     *                         subnetType: SubnetType.PRIVATE_ISOLATED,
     *                     },
     *                 },
     *             }
     *         );
     *
     * </pre>
     *
     * @param databaseSecret
     * @param vpc
     * @param securityGroup
     * @return
     */
    private DatabaseCluster getDatabaseCluster(DatabaseSecret databaseSecret, IVpc vpc, SecurityGroup securityGroup) {


        final Credentials credentials = Credentials.fromSecret(databaseSecret);

        // Create Aurora cluster DB
        final IClusterEngine dbEngine = DatabaseClusterEngine
                .auroraPostgres(AuroraPostgresClusterEngineProps.builder()
                        .version(AuroraPostgresEngineVersion.VER_15_2)
                        .build());

//        Aspects.of()
        DatabaseCluster dbCluster = new DatabaseCluster(this, "PostgresSeverlessV2", DatabaseClusterProps.builder().
                engine(dbEngine).
//                storageEncrypted(true).
        iamAuthentication(true).
                instanceProps(InstanceProps.builder()
                        .instanceType(new InstanceType("ServerlessV2"))
                        .vpc(vpc)
                        .securityGroups(Collections.singletonList(securityGroup))
                        .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_ISOLATED).build())
                        .build()).

                build());
        Aspects.of(dbCluster).add(new IAspect() {
            @Override
            public void visit(@NotNull IConstruct node) {
                if (node instanceof CfnDBCluster) {
                    CfnDBCluster dbNode = (CfnDBCluster) node;
//                    dbNode
                    dbNode.setServerlessV2ScalingConfiguration(CfnDBCluster.ServerlessV2ScalingConfigurationProperty.builder()
                            .maxCapacity(0.5)
                            .maxCapacity(1)
                            .build());

                }
            }
        });

        return dbCluster;
    }
}

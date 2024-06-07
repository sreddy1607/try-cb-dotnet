def branch = env.BRANCH_NAME ?: "ecr"
def namespace = env.NAMESPACE ?: "dev"
def workingDir = "/home/jenkins/agent"

pipeline {
  agent {
    kubernetes {
      yaml """
        apiVersion: v1
        kind: Pod
        spec:
          serviceAccountName: jenkins
          volumes:
            - name: dockersock
              hostPath:
                path: /var/run/docker.sock
            - name: varlibcontainers
              emptyDir: {}
            - name: jenkins-trusted-ca-bundle
              configMap:
                name: jenkins-trusted-ca-bundle
                defaultMode: 420
                optional: true
          containers:
            - name: jnlp
              securityContext:
                privileged: true
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
            - name: node
              image: registry.access.redhat.com/ubi8/nodejs-16:latest
              tty: true
              command: ["/bin/bash"]
              securityContext:
                privileged: true
              workingDir: "${workingDir}"
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: "${workingDir}"
                - name: BRANCH
                  value: "${branch}"
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
            - name: cammismsbuild
              image: 136299550619.dkr.ecr.us-west-2.amazonaws.com/cammismspapp:1.0.34
              tty: true
              command: ["/bin/bash"]
              securityContext:
                privileged: true
              workingDir: "${workingDir}"
              envFrom:
                - configMapRef:
                    name: jenkins-agent-env
                    optional: true
              env:
                - name: HOME
                  value: "${workingDir}"
                - name: BRANCH
                  value: "${branch}"
                - name: NEXUS_ACCESS_TOKEN
                  valueFrom:
                    secretKeyRef:
                      name: jenkins-token-qqsb2
                      key: token
                - name: GIT_SSL_CAINFO
                  value: "/etc/pki/tls/certs/ca-bundle.crt"
              volumeMounts:
                - name: jenkins-trusted-ca-bundle
                  mountPath: /etc/pki/tls/certs
      """
    }
  }

  options {
    disableConcurrentBuilds()
    timeout(time: 5, unit: 'HOURS')
    skipDefaultCheckout()
    buildDiscarder(logRotator(numToKeepStr: '70'))
  }

  environment {
    env_git_branch_type = "feature"
    env_git_branch_name = ""
    env_current_git_commit = ""
    env_skip_build = "false"
    env_stage_name = ""
    env_step_name = ""
    
    NEXUS_URL = "https://nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov"
    NEXUS_REPOSITORY = "cammis-msbuild-repo"
    NEXUS_CREDENTIALS = credentials('nexus-credentials')
    CERTIFICATE_PATH = "/etc/pki/tls/certs/ca-bundle.crt"
   
  }

  stages {
    stage("Initialize") {
      steps {
        container(name: "cammismsbuild") {
          script {
            properties([
              parameters([])
            ])

            env_stage_name = "initialize"
            env_step_name = "checkout"

            deleteDir()
            echo 'Checkout source and get the commit ID'
            env_current_git_commit = checkout(scm).GIT_COMMIT

            echo 'Loading properties file'
            env_step_name = "load properties"
            // load the pipeline properties
            // load(".jenkins/pipelines/Jenkinsfile.ecr.properties")

            env_step_name = "set global variables"
            echo 'Initialize Slack channels and tokens'
          }
        }
      }
    }

    stage('Setup HTTPS Certificates') {
      steps {
        container('cammismsbuild') { 
          echo 'Cleaning existing HTTPS certificates'
          sh'''
          echo | openssl s_client -connect nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov:443 -servername nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov 2>/dev/null | openssl x509 -inform pem -out nexus.crt
                        ls -l
                        CERT_DIR="/etc/pki/ca-trust/source/anchors/"
                        cp nexus.crt /etc/pki/ca-trust/source/anchors/
                        chmod 644 ${CERT_DIR}$(basename nexus.crt)
			           update-ca-trust
					   
					   echo | openssl s_client -connect nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov:443 -servername nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov
					   '''
        }
      }
    }

    stage('Restore Dependencies') {
      steps {
        container('cammismsbuild') {
           
          sh 'dotnet restore'
        }
      }
    }

    stage('Build') {
      steps {
        container('cammismsbuild') {
          sh 'dotnet build --configuration Release'
        }
      }
    }

    stage('Publish') {
      steps {
        container('cammismsbuild') {
          sh 'dotnet publish --configuration Release --output ./publish'
        }
      }
    }

    stage('Pack NuGet Package') {
      steps {
        container('cammismsbuild') {
          sh 'dotnet pack -o ./publish'
        }
      }
    }

    stage('Push to Nexus') {
      steps {
        container('cammismsbuild') {
		      script{
// Write custom settings.xml file
 writeFile file: 'NuGet.Config', text: """<?xml version="1.0" encoding="utf-8"?>
<configuration>
   <config>
    <add key="http-client.sslProtocols" value="Tls12,Tls13" />
    <add key="allowUntrustedRoot" value="true" />
  </config>
   <packageSources>
      <add key="Nexus" value="https://nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov/repository/nuet-hosted/" />
   </packageSources>
  <packageSourceCredentials>
    <Nexus>
      <add key="Username" value="nexuspoc" />
      <add key="ClearTextPassword" value="Nexus123!" />
    </Nexus>
  </packageSourceCredentials>
</configuration>
"""
}
              withCredentials([string(credentialsId: 'nexus-nugetkey', variable: 'NUGET_API_KEY')]) {
sh '''

                          dotnet nuget push publish/* -k $NUGET_API_KEY -s Nexus 
		'''
        }
      }
    }
  }
}
}

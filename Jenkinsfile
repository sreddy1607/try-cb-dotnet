def branch = env.BRANCH_NAME ?: "feature/devops"
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
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        SONAR_TIMEOUT = 3
        SONAR_SLEEP = 10000
        SONAR_ERROR_MSG = "QUALITY GATE ERROR: Pipeline set to unstable"
        SONAR_BUILD_RESULT = "UNSTABLE"
        SONAR_SLACK_MSG = "Quality Gate Passed"

        NEXUS_URL = "https://nexusrepo-tools.apps.bld.cammis.medi-cal.ca.gov"
        NEXUS_REPOSITORY = "surge-tar-msbuild"
        NEXUS_CREDENTIALS = credentials('nexus-credentials')
        CERTIFICATE_PATH = "/etc/pki/tls/certs/ca-bundle.crt"
        DOTNET_NUGET_SIGNATURE_VERIFICATION = "false"
    }

    stages {
        stage('Initialize and Cloning Repositories') {
            steps {
                script {
                    def repos = [
                        [name: 'tar-surge-app', url: 'https://github.com/ca-mmis/tar-surge-app.git'],
                        [name: 'tar-etar-web', url: 'https://github.com/ca-mmis/tar-etar-web.git'],
                        [name: 'tar-surge-client', url: 'https://github.com/ca-mmis/tar-surge-client.git']
                    ]

                    for (repo in repos) {
                        dir(repo.name) {
                            git branch: 'master', credentialsId: 'github-key', url: repo.url
                        }
                    }
                }
            }
        }

        stage('Restore Dependencies') {
            steps {
                container('cammismsbuild') {
                    script {
                        try {
                            sh '''
                                dotnet nuget locals all --clear
                                dotnet restore tar-surge-app/Cammis.Surge.Full.sln --verbosity detailed /p:EnableWindowsTargeting=true
                            '''
                        } catch (Exception e) {
                            error "Failed to restore dependencies: ${e.message}"
                        }
                    }
                }
            }
        }

        stage('MS Build and Publish') {
            steps {
                container('cammismsbuild') {
                    sh '''
                        dotnet build tar-surge-app/Cammis.Surge.Full.sln --configuration Release --verbosity detailed /p:EnableWindowsTargeting=true
                        dotnet publish tar-surge-app/Cammis.Surge.Full.sln --configuration Release --output ./publish /p:EnableWindowsTargeting=true
                    '''
                }
            }
        }

        stage('Pack NuGet Package') {
            steps {
                container('cammismsbuild') {
                    sh 'dotnet pack tar-surge-app/Cammis.Surge.Full.sln --configuration Release -o ./publish /p:EnableWindowsTargeting=true'
                }
            }
        }
stage('Sonar Scan') {
            steps {
               script {
                   withSonarQubeEnv('sonar_server') {
                      container(name: "cammismsbuild") {
                                            sh """
                                                 echo ' wget and unzip file'
                                                 mkdir -p /home/jenkins/agent/.sonar/native-sonar-scanner
                                                 wget --quiet https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/sonar-scanner-cli-6.1.0.4477-linux-x64.zip
                                                 unzip -q sonar-scanner-cli-6.1.0.4477-linux-x64.zip -d /home/jenkins/agent/.sonar/native-sonar-scanner
                                               """
            }
                      container(name: "jnlp") {
                                            sh """
                                                 echo ' doing sonar-scanner call'
                                                 /home/jenkins/agent/.sonar/native-sonar-scanner/sonar-scanner-6.1.0.4477-linux-x64/bin/sonar-scanner -Dproject.settings=${WORKSPACE}/devops/sonar/sonar-project.properties
                                               """
            }
          }
        }
      }
    }

        stage("Quality Gate") {
            steps {
                       container(name: "jnlp") {
               script {
                        sh """
                             echo "######################################################################################\n"
                             echo "####       RUNNING SONARQUBE QUALITY GATE CHECK                                                                  ####\n"
                             echo "SONAR TIMEOUT  ${SONAR_TIMEOUT}"
                             cat ${WORKSPACE}/devops/sonar/sonar-project.properties
                             echo "#################################################################################\n"
                           """
            sleep time: SONAR_SLEEP, unit: "MILLISECONDS"

            timeout(time: SONAR_TIMEOUT, unit: 'MINUTES') {
              def qualGate = waitForQualityGate()
              if (qualGate.status == "OK") {
                echo "PIPELINE INFO: ${qualGate.status}\n"
              } else if (qualGate.status == "NONE") {
                SONAR_SLACK_MSG = "PIPELINE WARNING: NO Quality Gate or projectKey associated with project \nCheck sonar-project.properties projectKey value is correct"
                echo "Quality gate failure: ${qualGate.status} \n ${SONAR_SLACK_MSG} \n#####################################################################################################################\n"
                currentBuild.result = SONAR_BUILD_RESULT
              } else {
                echo "Quality Gate: ${qualGate.status} \n ${SONAR_SLACK_MSG} \n#####################################################################################################################\n"
                slackNotification("pipeline","${APP_NAME}-${env_git_branch_name}: <${BUILD_URL}|build #${BUILD_NUMBER}> ${SONAR_SLACK_MSG}.", "#F6F60F","true")
                currentBuild.result = SONAR_BUILD_RESULT
              }
            }
          }
        }
      }
    }
       
        stage('Push to Nexus') {
            steps {
                container('cammismsbuild') {
                    script {
                        
                            sh '''
                            ls -l 
                           ls -l tar-surge-app/
                           ls -l publish/
                           
                                for file in publish/*.nupkg; do
                                    curl -k -v -u ${NEXUS_CREDENTIALS_USR}:${NEXUS_CREDENTIALS_PSW} \
                                    -F "nuget.asset=@$file" "${NEXUS_URL}/service/rest/v1/components?repository=${NEXUS_REPOSITORY}"
                                done
                            '''
                        
                    }
                }
            }
        }
    }
}

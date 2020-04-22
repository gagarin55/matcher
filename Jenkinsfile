pipeline {
    agent {
        label 'buildagent'
    }
    environment {
        SBT_HOME = tool name: 'sbt-1.2.6', type: 'org.jvnet.hudson.plugins.SbtPluginBuilder$SbtInstallation'
        SBT_THREAD_NUMBER = 7
        PATH = "${env.SBT_HOME}/bin:${env.PATH}"
    }
    stages {
        stage('Compile') {
            steps {
                sh 'sbt compile'
            }
        }
        stage('Build Docker') {
            steps {
                sh 'sbt dex-it/docker'
            }
        }
        stage('Test') {
            steps {
                sh 'sbt test'
            }
        }
    }
}


pipeline {
    agent any
    environment {
        CI = 'true'
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


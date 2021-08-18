DEPLOY_TYPE=$1
LINE_TYPE=$2

if [ $LINE_TYPE == "offline" ]; then
  echo "######  Load docker images ######"
  tars=$(ls ./images | grep '.tar')
  for i in tars ; do
    echo "Load "i
    docker load -i i
  done
  echo "######  Load docker images done !  ######"
fi

if [ DEPLOY_TYPE == "docker-compose" ]; then
    echo "######  Deploy by docker-compose ######"
    deploy_docker()
fi

if [ DEPLOY_TYPE == "kubernetes" ]; then
    echo "######  Deploy by kubernetes ######"
    deploy_kubernetes()
fi


function deploy_docker() {
  docker-compose -f deploy/docker-compose/zeus.yaml up -d
}

function deploy_kubernetes() {
  kubectl create -f zeus.yaml
  kubectl create -f zeus-ui.yaml
}
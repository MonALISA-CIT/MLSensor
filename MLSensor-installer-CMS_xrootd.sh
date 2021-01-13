#!/bin/bash

REQUIRED_JAVA_VERSION="1.6"

myreplace(){
        a=`echo $1 | sed 's/\//\\\\\//g'`
        b=`echo $2 | sed 's/\//\\\\\//g'`
        cat | sed "s/$a/$b/g"
}

replace_in_file(){
        cat $1 | myreplace "$2" "$3" > $1.new
        rm -f $1
        mv $1.new $1
}

echo
echo "This will download and install MLSensor and start it afterwards"
echo

# Username...
BATCH_MODE=true

KERNEL=`uname -s`
CRONTAB=${CRONTAB:-Y}
STARTMLSENSOR=${STARTMLSENSOR:-Y}
USEU=${USEU:-E}
PREFIX1=$PWD/MLSensorDist
PREFIX=${PREFIX:-$PREFIX1}

PLATFORM=`uname -m`
FULL_PLATFORM=$KERNEL-$PLATFORM
WGET="wget -q "

echo "Checking for local Java installation"
#Check for java first
COMPACT_REQ_VERSION=`echo $REQUIRED_JAVA_VERSION | sed -e 's;\.;0;g'`

if [ -z "${JAVA_HOME}" ]; then
	JAVA_CMD="java"
else
	JAVA_CMD="${JAVA_HOME}/bin/java"
fi

LOCAL_VERSION=`${JAVA_CMD} -version 2>&1 | grep "java version" | awk '{ print substr($3, 2, length($3)-2); }'`

if [ -z "${LOCAL_VERSION}" ]; then
	echo "Unable to determine local java version"
	exit 5
fi
LOCAL_COMPACT_VERSION=`echo $LOCAL_VERSION | awk '{ print substr($1, 1, 3); }' | sed -e 's;\.;0;g'`
if [ $LOCAL_COMPACT_VERSION -ge $COMPACT_REQ_VERSION ]; then
	echo "Your local java version:$LOCAL_VERSION is ok!"
else
	echo "Your local java version:$LOCAL_VERSION is not ok. You need at least version:$REQUIRED_JAVA_VERSION Sun/Oracle JVM"
	exit 5
fi

echo
echo "Preparing install dir..."
mkdir -p $PREFIX
cd $PREFIX

PREFIX=$PWD
if [ -x $PREFIX/MLSensor/bin/MLSensor ]; then
    echo "Seems that MLSensor is installed. Will try to stop it first (if running)"
    $PREFIX/MLSensor/bin/MLSensor stop
fi

rm -rf MLSensor

# Install MLSensor
MLSENSOR_KIT="MLSensor.tgz"
echo "Downloading MLSensor..."
if [ "$USEU" == "E" -o "$USEU" == "e" ] ; then
	$WGET http://monalisa.cern.ch/MLSensor/$MLSENSOR_KIT -O $MLSENSOR_KIT ||
	$WGET http://monalisa.caltech.edu/MLSensor/$MLSENSOR_KIT -O $MLSENSOR_KIT
else
	$WGET http://monalisa.caltech.edu/MLSensor/$MLSENSOR_KIT -O $MLSENSOR_KIT ||
	$WGET http://monalisa.cern.ch/MLSensor/$MLSENSOR_KIT -O $MLSENSOR_KIT 
fi

if [ ! -s "$MLSENSOR_KIT" ] ; then
	echo "Failed to download MLSensor."
	echo "Please check http (TCP port 80) connectivity to monalisa.cern.ch and monalisa.caltech.edu"
	exit 3
fi

gzip -dc $MLSENSOR_KIT | tar xf -

rm -f $MLSENSOR_KIT

xrootclustername=`locate system.cnf | grep xroot | xargs cat | grep SE_NAME | grep ^export | sort -u | cut -d= -f2 | sed 's/"//g' | sed "s/'//g" | tail -n1`
targetml=""

if [ -z "$xrootdclustername" ]; then
	sitename=`wget -q -O - "http://xrootd.t2.ucsd.edu/services/getClosestSite.jsp"`

	xrootdclustername="CMS::$sitename::MLSensor"
	
	targetml=`locate system.cnf | grep xroot | xargs cat | grep MONALISA_HOST | grep ^export | sort -u | cut -d= -f2 | sed 's/"//g' | sed "s/'//g" | tail -n1`
fi

if [ -z "$targetml" ]; then
	targetml=`wget -q -O - "http://xrootd.t2.ucsd.edu/services/getClosestSite.jsp?ml_ip=true"`
fi

if [ -z "$targetml" ]; then
	targetml="localhost"
fi

targetml="$targetml:8884"

echo -n "Cluster name (default $xrootdclustername): "
read -e x

if [ ! -z "$x" ]; then
	xrootdclustername="$x"
fi

echo -n "Target MonALISA service (default $targetml): "
read -e x

if [ ! -z "$x" ]; then
	targetml="$x"
fi

replace_in_file MLSensor/etc/mlsensor.properties "localhost:56884" "$targetml"
replace_in_file MLSensor/etc/mlsensor.properties "cluster.name=MLSensor" "cluster.name=$xrootdclustername"

if [ "$STARTMLSENSOR" == "y" -o "$STARTMLSENSOR" == "Y" ] ; then
	echo
	echo "Starting MLSensor with $PREFIX/MLSensor/bin/MLSensor start ..."
	$PREFIX/MLSensor/bin/MLSensor start
fi

echo
echo "Done."

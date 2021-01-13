#!/bin/bash
#
#

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
JAVA_VER="java6"
WGET="wget -q "
echo
echo "Preparing install dir..."
mkdir -p $PREFIX
cd $PREFIX
PREFIX=$PWD

if [ -z "$JAVA_HOME" ]; then
	JAVA_HOME=/usr
fi

if [ -z "$JAVA_HOME" -o ! -f "$JAVA_HOME/bin/java" -o ! -x "$JAVA_HOME/bin/java" ]; then
	# Install Java
	JAVA_KIT="$JAVA_VER-$FULL_PLATFORM.tgz"
	echo "Downloading $JAVA_VER SDK for $FULL_PLATFORM..."
	$WGET http://monalisa.cern.ch/download/java/$JAVA_KIT -O $JAVA_KIT 
	if [ ! -s "$JAVA_KIT" ] ; then
	        echo "Failed to download java. Please check that you have 'wget' in PATH"
	        echo "and you can access monalisa.cacr.caltech.edu or monalisa.cern.ch."
	        exit 2
	fi
	echo "Installing Java..."
	gzip -dc $JAVA_KIT | tar xf -
	
	rm -f $JAVA_KIT
	
	JAVA_HOME="$PREFIX/java"
else
	echo "Using system java from $JAVA_HOME"
fi

if [ -x $PREFIX/MLSensor/bin/MLSensor ]; then
    echo 'Seems that MLSensor is installed. Will try to stop it first (if running)'
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
	echo "Please check connectivity to monalisa.cern.ch and monalisa.caltech.edu"
	exit 3
fi

gzip -dc $MLSENSOR_KIT | tar xf -

rm -f $MLSENSOR_KIT

xrootdclustername=${MLSENSOR_CLUSTERNAME}
targetml=${MLSENSOR_TARGETML}

isxrootd="n"

if [ ! -z "$SE_NAME" ]; then
    xrootdclustername="$SE_NAME"
    isxrootd="y"
    targetml=`wget -q -O - "http://alimonitor.cern.ch/services/getClosestSite.jsp?ml_ip=true"`
fi

if [ -z "$xrootdclustername" ]; then
    xrootdclustername=`locate system.cnf | grep xroot | xargs cat | grep SE_NAME | grep ^export | sort -u | cut -d= -f2 | sed 's/"//g' | sed "s/'//g" | tail -n1`
    
    if [ ! -z "$xrootdclustername" ]; then
	isxrootd="y"
    fi
fi

if [ -z "$xrootdclustername" ]; then
    sitename=`wget -q -O - "http://alimonitor.cern.ch/services/getClosestSite.jsp"`

    xrootdclustername="ALICE::$sitename::MLSensor"
else	
    if [ -z "$targetml" ]; then
	targetml=`locate system.cnf | grep xroot | xargs cat | grep MONALISA_HOST | grep ^export | sort -u | cut -d= -f2 | sed 's/"//g' | sed "s/'//g" | tail -n1`
    fi
fi

if [ -z "$targetml" ]; then
    targetml=`wget -q -O - "http://alimonitor.cern.ch/services/getClosestSite.jsp?ml_ip=true"`
fi

if [ -z "$targetml" ]; then
    targetml="localhost"
fi

targetml="$targetml:8884"

if [ -z "$MLSENSOR_CLUSTERNAME" ]; then
    echo -n "Cluster name (default $xrootdclustername): "
    read -e x

    if [ ! -z "$x" ]; then
	xrootdclustername="$x"
    fi
fi

if [ -z "$MLSENSOR_TARGETML" ]; then
    echo -n "Target MonALISA service (default $targetml): "
    read -e x

    if [ ! -z "$x" ]; then
	targetml="$x"
    fi
fi

replace_in_file MLSensor/etc/mlsensor.properties "localhost:56884" "$targetml"
replace_in_file MLSensor/etc/mlsensor.properties "cluster.name=MLSensor" "cluster.name=$xrootdclustername"

if [ "$isxrootd" = "y" ]; then
    replace_in_file MLSensor/etc/mlsensor.properties "cluster.name.dynamic=true" "cluster.name.dynamic=false"
    replace_in_file MLSensor/etc/mlsensor.properties "cluster.name.suffix=_Nodes" "cluster.name.suffix=_xrootd_Nodes"
    
    (
    echo ""
    echo "# Be compatible with legacy Perl xrootd monitoring companion"
    echo "rewrite.parameter.names=true"
    echo "cluster.name.suffix.monXrdSpace=_manager_xrootd_Services"
    echo ""
    echo "# Monitor xrootd disk space usage"
    echo "# ************* IMPORTANT: only enable the following line on the redirector itself"
    echo "#mlsensor.modules=monXrdSpace"
    echo ""
    echo "# Run the disk space checking every 5 minutes"
    echo "monXrdSpace.execDelay=300"
    echo ""
    echo "# Change this to the full path to xrd if it cannot be found in PATH, and replace 1094 with redirector port number"
    echo "lia.Monitor.modules.monXrdSpace.args=xrd 1094"
    ) >> MLSensor/etc/mlsensor.properties
fi

replace_in_file MLSensor/etc/mlsensor_env "#JAVA_HOME=" \
"JAVA_HOME=$JAVA_HOME \n\
PATH=\$JAVA_HOME/bin:\$PATH \n\
export JAVA_HOME PATH"

if [ "$STARTMLSENSOR" == "y" -o "$STARTMLSENSOR" == "Y" ] ; then
	echo
	echo "Starting MLSensor with $PREFIX/MLSensor/bin/MLSensor start ..."
	$PREFIX/MLSensor/bin/MLSensor start
fi

echo
echo "Done."

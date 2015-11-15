#!/bin/bash
#
# Script for starting Docker ePAD, a Quantitative Imaging Platform from the Rubin Lab at Stanford, using Docker
#
#Copyright (c) 2015 The Board of Trustees of the Leland Stanford Junior University
#All rights reserved.
#
# Please do not Redistribute. The latest copy of this script is available from http://epad.stanford.edu/
#
#THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
#INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
#DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
#SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
#SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
#WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
#USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#
UNAME=`uname`
echo "Starting ePad using docker"
docker start mysql
sleep 3
docker start exist
docker start dcm4chee
docker start epad_web
if [ "$UNAME" == "Darwin" ]; then
	echo "Please WAIT for at least TWO minutes and then navigate to http://`docker-machine ip default`:8080/epad/ in your browser and login as admin/admin"
else
	echo "Please WAIT for at least TWO minutes and the navigate to http://`hostname`:8080/epad/ in your browser and login as admin/admin"
fi

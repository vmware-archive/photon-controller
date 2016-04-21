HOME=/home/photon
mkdir $HOME
groupadd photon
useradd -G users,photon,sudo photon -d $HOME
echo photon:changeme | chpasswd
echo "photon ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
chown -R photon:photon $HOME

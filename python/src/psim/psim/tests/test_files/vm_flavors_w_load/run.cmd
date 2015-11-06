# Configurations
seed 10000
# log /tmp/psim.log DEBUG

# Configure simulator enviroment
load_flavors common/vm.yml
load_flavors common/ephemeral-disk.yml
load_datastores common/datastores.yml

# generate schedulers and hosts
auto_tree vm_flavors_w_load/tree.yml

# Execute simulator
run vm_flavors_w_load/requests.yml

# Results
print_results True True True
check_results vm_flavors_w_load/results.yml

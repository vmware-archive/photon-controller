# Configurations
seed 10000
# log /tmp/psim.log DEBUG

# Configure simulator enviroment
load_flavors common/vm.yml
load_flavors common/ephemeral-disk.yml
load_datastores common/datastores.yml

# generate schedulers and hosts
auto_tree varying_vm_flavors/tree.yml

# Execute simulator
run varying_vm_flavors/requests.yml

# Results
print_results True True True
check_results varying_vm_flavors/results.yml

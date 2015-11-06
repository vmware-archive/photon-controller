# Configurations
seed 10000
# log /tmp/psim.log DEBUG

# Configure simulator enviroment
load_flavors common/vm.yml
load_flavors common/ephemeral-disk.yml
load_datastores common/datastores.yml

# load schedulers and hosts
load_tree no_constraints/schedulers.yml

# Execute simulator
run no_constraints/requests.yml

# Results
print_results
check_results no_constraints/results.yml

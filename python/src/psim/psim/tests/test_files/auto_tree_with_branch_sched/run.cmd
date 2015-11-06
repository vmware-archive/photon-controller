# Configurations
seed 10000
# log /tmp/psim.log DEBUG

# Configure simulator enviroment
load_flavors common/vm.yml
load_flavors common/ephemeral-disk.yml
load_datastores common/datastores.yml

# generate schedulers and hosts
auto_tree auto_tree_with_branch_sched/tree.yml

# Execute simulator
run auto_tree_with_branch_sched/requests.yml

# Results
print_results True True True
check_results auto_tree_with_branch_sched/results.yml

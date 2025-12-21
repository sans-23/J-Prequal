import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os

# Set style
sns.set_theme(style="whitegrid")
plt.rcParams.update({'figure.figsize': (12, 6)})

# Read Data
df = pd.read_csv('simulation_results.csv')

# Ensure output dir
os.makedirs('plots', exist_ok=True)

def parse_config(config_str, key):
    """Extracts value for a key from the config string."""
    try:
        # Format: SimulationConfig[... key=value| ...]
        parts = config_str.split(key + '=')
        if len(parts) > 1:
            return float(parts[1].split('|')[0].strip(' ]'))
    except:
        return None
    return None

def plot_experiment(experiment_name, x_param, x_label, metrics, log_scale_x=False):
    data = df[df['experiment'] == experiment_name].copy()
    
    if data.empty:
        print(f"No data for experiment: {experiment_name}")
        return

    # Extract X parameter
    data[x_label] = data['config'].apply(lambda x: parse_config(x, x_param))
    data = data.sort_values(x_label)

    # Plot each metric
    for metric in metrics:
        plt.figure()
        sns.lineplot(data=data, x=x_label, y=metric, hue='strategy', marker='o', linewidth=2.5)
        
        plt.title(f'{experiment_name}: {x_label} vs {metric}')
        plt.xlabel(x_label)
        plt.ylabel(metric)
        if log_scale_x:
            plt.xscale('log')
        
        filename = f"plots/{experiment_name}_{metric}.png"
        plt.savefig(filename)
        print(f"Saved {filename}")
        plt.close()

# 1. Concurrency Sweep
plot_experiment(
    'ConcurrencySweep', 
    'numClients', 
    'Clients', 
    ['throughput', 'p99', 'max'],
    log_scale_x=True
)

# 2. Probe Count Sweep
plot_experiment(
    'ProbeCountSweep', 
    'prequalProbeCount', 
    'ProbeCount (d)', 
    ['p99']
)

# 3. Quantile Sweep
plot_experiment(
    'QuantileSweep', 
    'prequalQuantile', 
    'Quantile (q)', 
    ['p99']
)

# 4. Pool Size Sweep
plot_experiment(
    'PoolSizeSweep', 
    'prequalPoolSize', 
    'PoolSize', 
    ['p99'],
    log_scale_x=True
)

# 5. Heterogeneity Sweep
plot_experiment(
    'HeterogeneitySweep', 
    'numClients', 
    'Clients', 
    ['p99', 'throughput', 'max'],
    log_scale_x=False
)

print("Plotting complete.")

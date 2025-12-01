from pathlib import Path
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns

ROOT = Path(__file__).resolve().parents[1]
DATAFILE = ROOT / 'results_filt.txt'
OUT_DIR = ROOT / 'reports'
PLOTS_DIR = OUT_DIR / 'plots'
PLOTS_DIR.mkdir(parents=True, exist_ok=True)

# Parse lines into rows
rows = []
with open(DATAFILE, 'r', encoding='utf-8') as f:
    for line in f:
        line = line.strip()
        if not line or line.startswith('=') or line.startswith('Sweep') or line.startswith('run') or line.startswith('-'):
            continue
        parts = line.split()
        # Expect 12 columns as in the file
        if len(parts) < 12:
            continue
        run = parts[0]
        fft = int(parts[1])
        width = int(parts[2])
        binpt = int(parts[3])
        pipeline = parts[4]
        arch = parts[5]
        maxMHz_s = parts[6]
        wns_s = parts[7]
        luts_s = parts[8]
        ffs_s = parts[9]
        dsps_s = parts[10]
        time_s = parts[11]
        # convert numbers with comma decimal separator to float
        def num(s):
            s = s.replace('.', '') 
            s = s.replace(',', '.')
            try:
                if '.' in s:
                    return float(s)
                return int(s)
            except:
                return np.nan
        maxMHz = num(maxMHz_s)
        wns = num(wns_s)
        time_sec = num(time_s)
        luts = num(luts_s)
        ffs = num(ffs_s)
        dsps = num(dsps_s)
        # parse pipeline flags like 'C:T,B1:T,B2:T'
        p_flags = { 'C': False, 'B1': False, 'B2': False }
        for token in pipeline.split(','):
            if ':' in token:
                k,v = token.split(':',1)
                p_flags[k] = (v.strip().upper() == 'T')

        rows.append({
            'run': int(run), 'fft': fft, 'width': width, 'binpt': binpt,
            'pipeline': pipeline, 'arch': arch, 'maxMHz': maxMHz, 'wns': wns,
            'luts': luts, 'ffs': ffs, 'dsps': dsps, 'time_s': time_sec,
            'C': p_flags['C'], 'B1': p_flags['B1'], 'B2': p_flags['B2']
        })

if not rows:
    print('No data parsed from', DATAFILE)
    raise SystemExit(1)

df = pd.DataFrame(rows)
# Quick cleanup
# Mark failing timing
df['timing_fail'] = df['wns'] <= 0

# pipeline count (how many pipeline stages enabled)
df['pipeline_count'] = df[['C','B1','B2']].sum(axis=1)

# Save a CSV snapshot
OUT_CSV = OUT_DIR / 'results_parsed.csv'
df.to_csv(OUT_CSV, index=False)
print('Parsed rows:', len(df), '->', OUT_CSV)

# Scatter: maxMHz vs LUTs colored by fft
plt.figure(figsize=(8,6))
palette = sns.color_palette('tab10', n_colors=df['fft'].nunique())
sns.scatterplot(data=df, x='luts', y='maxMHz', hue='fft', style='width', palette=palette)
plt.title('Max MHz vs LUTs (colored by fft, style by width)')
plt.xlabel('LUTs')
plt.ylabel('Max (MHz)')
plt.tight_layout()
plt.savefig(PLOTS_DIR / 'scatter_mhz_vs_luts.png', dpi=150)
plt.close()
print('Saved scatter_mhz_vs_luts.png')

# Max MHz vs DSPs (overall)
plt.figure(figsize=(8,6))
sns.scatterplot(data=df, x='dsps', y='maxMHz', hue='fft', style='width', palette=palette)
plt.title('Max MHz vs DSPs (colored by fft, style by width)')
plt.xlabel('DSPs')
plt.ylabel('Max (MHz)')
plt.tight_layout()
plt.savefig(PLOTS_DIR / 'scatter_mhz_vs_dsps.png', dpi=150)
plt.close()
print('Saved scatter_mhz_vs_dsps.png')

# Heatmap: avg WNS for fft x width
pivot = df.pivot_table(index='fft', columns='width', values='wns', aggfunc='mean')
plt.figure(figsize=(8,6))
sns.heatmap(pivot, annot=True, fmt='.2f', cmap='coolwarm', center=0)
plt.title('Average WNS (ns) by FFT size and width')
plt.tight_layout()
plt.savefig(PLOTS_DIR / 'wns_heatmap.png', dpi=150)
plt.close()
print('Saved wns_heatmap.png')

# Per-FFT scatter plots colored by pipeline pattern
unique_ffts = sorted(df['fft'].unique())
pipeline_order = sorted(df['pipeline'].unique())
palette = sns.color_palette('tab10', n_colors=max(3, len(pipeline_order)))
for n in unique_ffts:
    sub = df[df['fft'] == n]
    if sub.empty:
        continue
    plt.figure(figsize=(8,6))
    sns.scatterplot(data=sub, x='luts', y='maxMHz', hue='pipeline', style='width', palette=palette, s=80)
    plt.title(f'Max MHz vs LUTs (fft={n}) — colored by pipeline')
    plt.xlabel('LUTs')
    plt.ylabel('Max (MHz)')
    plt.tight_layout()
    outname = PLOTS_DIR / f'scatter_mhz_vs_luts_fft_{n}.png'
    plt.savefig(outname, dpi=150)
    plt.close()
    print(f'Saved {outname.name}')

    # Per-FFT: alternate scatter using DSPs on x-axis
    plt.figure(figsize=(8,6))
    sns.scatterplot(data=sub, x='dsps', y='maxMHz', hue='pipeline', style='width', palette=palette, s=80)
    plt.title(f'Max MHz vs DSPs (fft={n}) — colored by pipeline')
    plt.xlabel('DSPs')
    plt.ylabel('Max (MHz)')
    plt.tight_layout()
    outname_dsps = PLOTS_DIR / f'scatter_mhz_vs_dsps_fft_{n}.png'
    plt.savefig(outname_dsps, dpi=150)
    plt.close()
    print(f'Saved {outname_dsps.name}')

# Per-pipeline WNS heatmaps
for _, grp in df.groupby(['C','B1','B2']):
    # derive a compact key for filename
    key = f"C{int(grp['C'].iloc[0])}_B1{int(grp['B1'].iloc[0])}_B2{int(grp['B2'].iloc[0])}"
    title_pipeline = grp['pipeline'].iloc[0]
    pivot_p = grp.pivot_table(index='fft', columns='width', values='wns', aggfunc='mean')
    if pivot_p.isnull().all().all():
        print(f'Skipping empty pivot for {key}')
        continue
    plt.figure(figsize=(8,6))
    sns.heatmap(pivot_p, annot=True, fmt='.2f', cmap='coolwarm', center=0)
    plt.title(f'Average WNS (ns) by FFT & width — pipeline={title_pipeline}')
    plt.tight_layout()
    outname = PLOTS_DIR / f'wns_heatmap_pipeline_{key}.png'
    plt.savefig(outname, dpi=150)
    plt.close()
    print(f'Saved {outname.name}')

# Compare architectures CT vs GS for identical configurations
# Find configurations where both arch variants exist (same fft,width,binpt,pipeline)
pair_index = ['fft','width','binpt','pipeline']
paired = df.pivot_table(index=pair_index, columns='arch', values=['luts','ffs','dsps','maxMHz','wns'], aggfunc='first')
if ('GS' in paired.columns.get_level_values(1)) and ('CT' in paired.columns.get_level_values(1)):
    # flatten columns
    paired.columns = ['{}_{}'.format(col[0], col[1]) for col in paired.columns]
    paired = paired.dropna(subset=['luts_GS','luts_CT'], how='all')
    paired_reset = paired.reset_index()
    OUT_PAIR_CSV = OUT_DIR / 'arch_pair_comparison.csv'
    paired_reset.to_csv(OUT_PAIR_CSV, index=False)
    print('Saved arch_pair_comparison.csv')

    # scatter: LUTs CT vs GS
    plt.figure(figsize=(7,7))
    sns.scatterplot(data=paired_reset, x='luts_CT', y='luts_GS', hue='fft', palette='tab10')
    mmin = min(paired_reset['luts_CT'].min(), paired_reset['luts_GS'].min())
    mmax = max(paired_reset['luts_CT'].max(), paired_reset['luts_GS'].max())
    plt.plot([mmin, mmax], [mmin, mmax], 'k--', alpha=0.6)
    plt.xlabel('LUTs (CT)')
    plt.ylabel('LUTs (GS)')
    plt.title('LUTs: CT vs GS (same fft,width,binpt,pipeline)')
    plt.tight_layout()
    plt.savefig(PLOTS_DIR / 'arch_compare_luts_CT_vs_GS.png', dpi=150)
    plt.close()
    print('Saved arch_compare_luts_CT_vs_GS.png')

    # scatter: FFs CT vs GS
    plt.figure(figsize=(7,7))
    sns.scatterplot(data=paired_reset, x='ffs_CT', y='ffs_GS', hue='fft', palette='tab10')
    mmin = min(paired_reset['ffs_CT'].min(), paired_reset['ffs_GS'].min())
    mmax = max(paired_reset['ffs_CT'].max(), paired_reset['ffs_GS'].max())
    plt.plot([mmin, mmax], [mmin, mmax], 'k--', alpha=0.6)
    plt.xlabel('FFs (CT)')
    plt.ylabel('FFs (GS)')
    plt.title('FFs: CT vs GS (same fft,width,binpt,pipeline)')
    plt.tight_layout()
    plt.savefig(PLOTS_DIR / 'arch_compare_ffs_CT_vs_GS.png', dpi=150)
    plt.close()
    print('Saved arch_compare_ffs_CT_vs_GS.png')

    # scatter: DSPs CT vs GS
    plt.figure(figsize=(7,7))
    sns.scatterplot(data=paired_reset, x='dsps_CT', y='dsps_GS', hue='fft', palette='tab10')
    mmin = min(paired_reset['dsps_CT'].min(), paired_reset['dsps_GS'].min())
    mmax = max(paired_reset['dsps_CT'].max(), paired_reset['dsps_GS'].max())
    plt.plot([mmin, mmax], [mmin, mmax], 'k--', alpha=0.6)
    plt.xlabel('DSPs (CT)')
    plt.ylabel('DSPs (GS)')
    plt.title('DSPs: CT vs GS (same fft,width,binpt,pipeline)')
    plt.tight_layout()
    plt.savefig(PLOTS_DIR / 'arch_compare_dsps_CT_vs_GS.png', dpi=150)
    plt.close()
    print('Saved arch_compare_dsps_CT_vs_GS.png')
    
    # scatter: Max Frequency (MHz) CT vs GS
    if 'maxMHz_CT' in paired_reset.columns and 'maxMHz_GS' in paired_reset.columns:
        plt.figure(figsize=(7,7))
        sns.scatterplot(data=paired_reset, x='maxMHz_CT', y='maxMHz_GS', hue='fft', palette='tab10')
        mmin = min(paired_reset['maxMHz_CT'].min(), paired_reset['maxMHz_GS'].min())
        mmax = max(paired_reset['maxMHz_CT'].max(), paired_reset['maxMHz_GS'].max())
        plt.plot([mmin, mmax], [mmin, mmax], 'k--', alpha=0.6)
        plt.xlabel('Max (MHz) (CT)')
        plt.ylabel('Max (MHz) (GS)')
        plt.title('Max Frequency (MHz): CT vs GS (same fft,width,binpt,pipeline)')
        plt.tight_layout()
        plt.savefig(PLOTS_DIR / 'arch_compare_maxMHz_CT_vs_GS.png', dpi=150)
        plt.close()
        print('Saved arch_compare_maxMHz_CT_vs_GS.png')
else:
    print('No paired CT/GS configurations found to compare.')

# Report configs present for one arch but not the other
key_cols = pair_index
unique_configs = df[key_cols + ['arch']].drop_duplicates()
gs_keys = set(map(tuple, unique_configs[unique_configs['arch'] == 'GS'][key_cols].values.tolist()))
ct_keys = set(map(tuple, unique_configs[unique_configs['arch'] == 'CT'][key_cols].values.tolist()))

ct_only = ct_keys - gs_keys
gs_only = gs_keys - ct_keys

def keys_to_df(keys_set):
    if not keys_set:
        return pd.DataFrame(columns=key_cols)
    return pd.DataFrame(list(keys_set), columns=key_cols)

ct_only_df = keys_to_df(ct_only)
gs_only_df = keys_to_df(gs_only)

OUT_CT_ONLY = OUT_DIR / 'ct_only_configs.csv'
OUT_GS_ONLY = OUT_DIR / 'gs_only_configs.csv'
ct_only_df.to_csv(OUT_CT_ONLY, index=False)
gs_only_df.to_csv(OUT_GS_ONLY, index=False)

print(f'CT-only configs: {len(ct_only_df)} saved to {OUT_CT_ONLY.name}')
print(f'GS-only configs: {len(gs_only_df)} saved to {OUT_GS_ONLY.name}')
if len(ct_only_df) > 0:
    print('Example CT-only (up to 10):')
    print(ct_only_df.head(10).to_string(index=False))
if len(gs_only_df) > 0:
    print('Example GS-only (up to 10):')
    print(gs_only_df.head(10).to_string(index=False))

# Simple summary stats printout
summary = df.groupby(['fft','width']).agg({'maxMHz':['mean','min','max'],'wns':['mean','min'],'luts':'mean','dsps':'max'})
summary_file = OUT_DIR / 'summary_by_fft_width.csv'
summary.to_csv(summary_file)
print('Saved summary_by_fft_width.csv')

print('Done. Plots and CSVs in', OUT_DIR)

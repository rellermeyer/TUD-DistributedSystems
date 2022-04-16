import seaborn as sns
import pandas as pd
import matplotlib.pyplot as plt
import scipy.stats as stats

if __name__ == "__main__":
    df_vv = pd.read_csv("csv_format/version_vector_formatted.csv")
    df_tmsp = pd.read_csv("csv_format/timestamp_formatted.csv")

    df_vv["algorithm"] = "version_vector"
    df_tmsp["algorithm"] = "timestamp"

    df = df_vv.append(df_tmsp, ignore_index=True)

    df["sites"] = df["sites"].astype(int)

    sns.set(rc={"figure.figsize": (11.7, 8.27)})

    plot_exec_time = sns.lineplot(
        data=df, x="sites", y="exec", hue="algorithm", err_style="band"
    )
    plot_exec_time.set_title("Execution time plot", fontsize=16)
    plot_exec_time.set_xlabel("Number of sites", fontsize=16)
    plot_exec_time.set_ylabel("Execution time (ms)", fontsize=16)
    plot_exec_time.set_xticks(range(22))

    plot_exec_time.get_figure().savefig("execution_times_timestamp_versionvector.png")

    plt.clf()

    plot_icd_count = sns.lineplot(
        data=df, x="sites", y="icd", hue="algorithm", err_style="band"
    )
    plot_icd_count.set_title("Number of inconsistencies detected plot", fontsize=16)
    plot_icd_count.set_xlabel("Number of sites", fontsize=16)
    plot_icd_count.set_ylabel("Number of inconsistencies detected", fontsize=16)
    plot_icd_count.set_xticks(range(21))

    plot_icd_count.get_figure().savefig("icds_timestamp_versionvector.png")

    vv_icds = df_vv["icd"]
    tt_icds = df_tmsp["icd"]

    vv_exec = df_vv["exec"]
    tt_exec = df_tmsp["exec"]

    print(f"One-way ANOVA for inconsistencies: {stats.f_oneway(vv_icds, tt_icds)}")
    print(f"One-way ANOVA for execution times: {stats.f_oneway(vv_exec, tt_exec)}")

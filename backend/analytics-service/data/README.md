# analytics-service — datasets

Training data for the PR risk / staleness model. This directory is a **sibling of
`app/`** on purpose: the Dockerfile only does `COPY app ./app`, so nothing here is
shipped into the runtime image. Training is a dev-time task; inference only needs
the serialised model in `app/artifacts/`.

| Directory | Contents | Committed? |
|---|---|---|
| `raw/` | Untouched source dumps exactly as downloaded/exported. Never edit in place. | No — gitignored |
| `processed/` | Feature-engineered tables and train/test splits produced by `app/ml/`. Always regenerable from `raw/`. | No — gitignored |
| `samples/` | Small (<1 MB) slices so teammates, tests, and CI can run the pipeline without the full dataset. | **Yes** |

`raw/` and `processed/` are gitignored because datasets are large and are data, not
source. Anything in them must be reproducible: a `raw/` file from a documented
download, a `processed/` file from a script. If a file is neither, it does not
belong here.

Record the provenance of every `raw/` file below — where it came from and when —
so the dataset can be rebuilt from scratch.

## Sources

<!-- e.g. | pr_history_2024.csv | GitHub GraphQL export, repos X/Y | 2026-08-18 | -->

| File | Source | Retrieved |
|---|---|---|

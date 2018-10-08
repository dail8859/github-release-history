import subprocess
import os
import json
from datetime import date, timedelta, datetime
from pathlib import Path

from github import Github

from config import *

repo_path = Path(REPO_NAME)
today = date.today()
yesterday = today - timedelta(days=1)

def git(*args):
    return subprocess.check_call(['git'] + list(args))

def process_repo(G, repo_data):
    print(repo_data["user"], repo_data["repo"])
    data_path = Path("data") / repo_data["user"] / (repo_data["repo"] + ".json")

    if data_path.exists():
        data = json.loads(open(str(data_path), "r").read())
    else:
        # The file didn't exist, make sure the full path exists just in case
        data_path.parent.mkdir(parents=True, exist_ok=True)
        data = {"releases": {}}

    repo = G.get_repo('{}/{}'.format(repo_data["user"], repo_data["repo"]))
    for release in repo.get_releases():
        if str(release.id) not in data["releases"]:
            release_data = {
                "created_at": release.created_at.isoformat(),
                "name": release.title or release.tag_name,
                "assets": {}
            }
            data["releases"][str(release.id)] = release_data
        else:
            release_data = data["releases"][str(release.id)]
            # Name could have changed
            release_data["name"] = release.title or release.tag_name

        for asset in release.get_assets():
            if str(asset.id) not in release_data["assets"]:
                asset_data = {
                    "created_at": release.created_at.isoformat(),
                    "name": asset.name,
                    "downloads": {str(yesterday): asset.download_count}
                }
                release_data["assets"][str(asset.id)] = asset_data
            else:
                asset_data = release_data["assets"][str(asset.id)]

                most_recent = sorted(asset_data["downloads"].keys())[-1]

                # Only update if "today" doesn't exist and the download count has changed
                if str(yesterday) != most_recent and asset_data["downloads"][most_recent] != asset.download_count:
                    asset_data["downloads"][str(yesterday)] = asset.download_count

    with open('data/{}/{}.json'.format(repo_data["user"], repo_data["repo"]), "w+") as f:
        json.dump(data, f, indent=1, sort_keys=True)


def main():
    # Clone or pull repository
    if not repo_path.exists():
        # Clone
        git("clone", "https://github.com/{}/{}.git".format(REPO_USER, REPO_NAME))
        os.chdir(str(repo_path))
    else:
        os.chdir(str(repo_path))
        git("pull", "origin", "master")

    repos = json.loads(open("repos.json", "r").read())
    G = Github(API_KEY, user_agent="github-release-history")
    G.get_rate_limit().core.remaining

    start_timestamp = datetime.utcnow()

    for repo_data in repos:
        process_repo(G, repo_data)

    end_timestamp = datetime.utcnow()

    commit_message = """Update for {}

Started at: {}
Finished at: {}
Rate limit remaining: {}""".format(today, start_timestamp.isoformat(), end_timestamp.isoformat(), G.get_rate_limit().core.remaining)

    git("add", "--all")
    git("commit", "-m", commit_message)
    git("push", f"https://{REPO_USER}:{API_KEY}@github.com/{REPO_USER}/{REPO_NAME}.git", "master")

if __name__ == '__main__':
    main()

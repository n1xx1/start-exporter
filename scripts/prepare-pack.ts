import { CurseforgeV1Client } from "@xmcl/curseforge";
import AdmZip from "adm-zip";
import { appendFile, copyFile, cp, mkdir, writeFile } from "node:fs/promises";
import { dirname, join, posix } from "node:path";
import {
  download,
  getGithubRelease,
  isMain,
  request,
  runInParallel,
} from "./common.js";
import * as R from "remeda";

const config = {
  repository: "StarT-Dev-Team/Star-Technology",
  release: "latest",
  clientAssetPattern: "^(?!.*server)Star[ ._-]*Technology[ ._-].+\\.zip$",
};

const curseforgeClient = new CurseforgeV1Client(
  process.env.CURSEFORGE_API_KEY ||
    (() => {
      throw new Error("Set the CURSEFORGE_API_KEY repository secret first");
    })()
);

function getFolderDest(type: number | null) {
  switch (type) {
    case 6:
      return "mods";
    case 12:
      return "resourcepacks";
    case 6552:
      return "shaderpacks";
  }
  return null;
}

export async function prepare(): Promise<void> {
  const release = await getGithubRelease(config);
  console.log(`Selected StarT release ${release.tag}: ${release.asset}`);

  const targetDir = "run";

  const archive = join(
    ".cache/releases",
    String(release.asset_id),
    release.asset
  );

  await download(release.download_url, archive, "sha256", release.sha256);
  await mkdir(targetDir);

  const zip = new AdmZip(archive);
  const manifest = JSON.parse(zip.readAsText("manifest.json")) as Manifest;
  const versions = runtimeVersions(manifest);

  const overridesPrefix = `${manifest.overrides}/`;
  const overridesToCopy = zip
    .getEntries()
    .filter((e) => !e.isDirectory && e.entryName.startsWith(overridesPrefix));

  console.log(`Copying ${overridesToCopy.length} overrides...`);
  await runInParallel(overridesToCopy, 8, async (entry) => {
    const target = join(
      targetDir,
      entry.entryName.slice(overridesPrefix.length)
    );
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, entry.getData());
  });

  const [curseforgeFiles, curseforgeProjects] = await Promise.all([
    curseforgeClient
      .getFiles(manifest.files.map((f) => f.fileID))
      .then((arr) => new Map(arr.map((f) => [f.id, f]))),
    curseforgeClient
      .getMods(manifest.files.map((f) => f.projectID))
      .then((arr) => new Map(arr.map((f) => [f.id, f]))),
  ]);

  await runInParallel(manifest.files, 8, async (entry) => {
    const file = curseforgeFiles.get(entry.fileID);
    const project = curseforgeProjects.get(entry.projectID);

    if (!file || !project || file.modId !== project.id) {
      throw new Error("missing file or mod");
    }

    const name = file.fileName;
    const destFolder = getFolderDest(project.classId);
    if (!destFolder) {
      throw new Error(
        `invalid project class ${project.classId} (${project.id})`
      );
    }

    const sha1 = file.hashes.find((hash) => hash.algo === 1)?.value;
    if (!sha1) {
      throw new Error(`missing sha-1: ${file.id} (${project.id})`);
    }

    let url = file.downloadUrl ?? null;
    if (!url) {
      url = await getModrinthDownloadUrl(sha1);
    }
    if (!url) {
      throw new Error(
        `missing download on curseforge and modrinth: ${file.id} )${project.id}`
      );
    }

    const targetPath = join(targetDir, destFolder, name);
    console.log(
      `Installing ${project.name} ${file.displayName} to ${targetPath}.`
    );

    const cached = join(".cache/files", `${file.id}`);
    await download(url, cached, "sha1", sha1);

    await mkdir(join(targetDir, destFolder), { recursive: true });
    await copyFile(cached, targetPath);
  });

  console.log(`Copying exporter overrides...`);
  await cp("exporter/overlay", targetDir, { recursive: true });
  await appendFile(
    "run/options.txt",
    `
onboardAccessibility:false
pauseOnLostFocus:false
`
  );

  await mkdir("run/export", { recursive: true });

  await writeFile(
    ".cache/provenance.json",
    JSON.stringify(
      {
        manifest: manifest,
        ...versions,
        source_commit: process.env.GITHUB_SHA,
        run_id: process.env.GITHUB_RUN_ID,
        run_attempt: process.env.GITHUB_RUN_ATTEMPT,
      },
      null,
      2
    ) + "\n"
  );

  console.log(`Done.`);
}

interface Manifest {
  minecraft: {
    version: string;
    modLoaders: { id: string; primary?: boolean }[];
  };
  overrides: string;
  version: string;
  files: { projectID: number; fileID: number }[];
}

export function runtimeVersions(manifest: Manifest) {
  const minecraft = manifest.minecraft.version;
  const loaders = manifest.minecraft.modLoaders;
  const loader =
    loaders.find((loader) => loader.primary) ??
    (loaders.length === 1 ? loaders[0] : undefined);
  if (
    !/^\d+\.\d+(?:\.\d+)?$/.test(minecraft) ||
    !loader ||
    !/^forge-\d+\.\d+\.\d+$/.test(loader.id)
  ) {
    throw new Error("Expected a Minecraft version and primary Forge loader in the pack manifest");
  }
  return { minecraft, forge: loader.id.slice("forge-".length) };
}

export async function getModrinthDownloadUrl(sha1: string) {
  const response = await request(
    `https://api.modrinth.com/v2/version_file/${sha1}?algorithm=sha1`,
    { headers: { "User-Agent": "StarT-Dev-Team/start-export-data" } }
  );

  interface ModrinthVersion {
    id: string;
    files: { url: string; hashes: { sha1: string } }[];
  }

  const version = (await response.json()) as ModrinthVersion;
  const file = version.files.find((file) => file.hashes.sha1 === sha1);
  return file?.url ?? null;
}

export function safeRelative(name: string): string {
  if (
    !name ||
    /[\\:\0]/.test(name) ||
    posix.isAbsolute(name) ||
    name.split("/").includes("..")
  ) {
    throw new Error(`Unsafe archive path: ${name}`);
  }

  const normalized = posix.normalize(name).replace(/\/$/, "");
  if (normalized === ".") {
    throw new Error(`Unsafe archive path: ${name}`);
  }

  return normalized;
}

if (isMain(import.meta.url)) {
  await prepare();
}

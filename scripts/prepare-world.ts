import AdmZip from "adm-zip";
import { mkdir, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { download, isMain } from "./common.js";

const config = {
  repository: "MinecraftDataGenerator/empty-world",
  commit: "8d25158dab7389390bc5121b1978e10a6feaaed3",
  sha256: "4020f231b2b1718093d6f317d659305e4de89f8da9ec871dc0f6a6c9113ec74d",
};

export async function prepareWorld(): Promise<void> {
  const archive = `.cache/worlds/empty-world-${config.commit}.zip`;

  await download(
    `https://codeload.github.com/${config.repository}/zip/${config.commit}`,
    archive,
    "sha256",
    config.sha256
  );

  const destination = "run/saves/empty-world";
  await mkdir(destination, { recursive: false });

  try {
    const prefix = `empty-world-${config.commit}/`;

    for (const entry of new AdmZip(archive).getEntries()) {
      if (entry.isDirectory) {
        continue;
      }

      if (!entry.entryName.startsWith(prefix)) {
        throw new Error("Unexpected world archive root");
      }

      const name = entry.entryName.slice(prefix.length);

      const target = join(destination, name);
      await mkdir(dirname(target), { recursive: true });
      await writeFile(target, entry.getData());
    }
  } catch (error) {
    await rm(destination, { recursive: true, force: true });
    throw error;
  }

  console.log(
    `Installed ${config.repository}@${config.commit} into ${destination}`
  );
}

if (isMain(import.meta.url)) {
  await mkdir("run/saves", { recursive: true });
  await prepareWorld();
}

import AdmZip from "adm-zip";
import { lstat, mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import { join, relative, sep } from "node:path";
import { checksum, isMain } from "./common.js";

async function listFiles(directory: string): Promise<string[]> {
  let files: string[] = [];
  for (const name of await readdir(directory)) {
    const path = join(directory, name);
    const info = await lstat(path);

    if (info.isDirectory()) {
      const contents = await listFiles(path);
      files = files.concat(contents);
      continue;
    }
    if (info.isFile()) {
      files.push(path);
      continue;
    }

    throw new Error(`Unsupported export entry: ${path}`);
  }
  return files.sort();
}

export async function packageExport(): Promise<void> {
  const source = "run/export";

  const marker = join(source, "complete.json");
  if (JSON.parse(await readFile(marker, "utf8")).status !== "success") {
    throw new Error(
      "Exporter must write complete.json with status=success after flushing all data"
    );
  }

  const files = await listFiles(source);
  const data = files.filter((file) => file !== marker);
  if (
    !data.length ||
    !(await Promise.all(data.map((file) => lstat(file)))).some(
      (info) => info.size > 0
    )
  ) {
    throw new Error("Export contains no data");
  }
  const archive = new AdmZip();
  for (const file of files) {
    archive.addFile(
      `data/${relative(source, file).split(sep).join("/")}`,
      await readFile(file)
    );
  }

  archive.addFile("provenance.json", await readFile(".cache/provenance.json"));

  await mkdir("dist", { recursive: true });

  await archive.writeZipPromise("dist/export.zip");

  await writeFile(
    "dist/SHA256SUMS",
    `${await checksum("dist/export.zip")}  export.zip\n`
  );
}

if (isMain(import.meta.url)) {
  await packageExport();
}

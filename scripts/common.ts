import { createHash } from "node:crypto";
import { createReadStream, createWriteStream } from "node:fs";
import { mkdir, rename, rm } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { setTimeout as delay } from "node:timers/promises";
import { pathToFileURL } from "node:url";

export function isMain(url: string): boolean {
  return (
    !!process.argv[1] && url === pathToFileURL(resolve(process.argv[1])).href
  );
}

export async function request(
  url: string,
  init: RequestInit = {}
): Promise<Response> {
  let attempt = 0;
  while (true) {
    try {
      const response = await fetch(url, {
        ...init,
        headers: { "User-Agent": "start-export-data", ...init.headers },
        signal: AbortSignal.timeout(120_000),
      });

      if (response.ok) {
        return response;
      }

      await response.body?.cancel();

      if (!nonRetryableErrors.includes(response.status)) {
        throw new NonRetryableError(`HTTP ${response.status}`);
      }
      throw new Error(`HTTP ${response.status}`);
    } catch (error) {
      attempt++;
      if (error instanceof NonRetryableError || attempt >= 3) throw error;
    }

    await delay(2 ** attempt * 1000);
  }
}

const nonRetryableErrors = [429, 500, 502, 503, 504];

class NonRetryableError extends Error {}

export async function checksum(
  file: string,
  algorithm = "sha256"
): Promise<string> {
  const hash = createHash(algorithm);
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest("hex");
}

export async function download(
  url: string,
  destination: string,
  algorithm: string,
  expected: string
): Promise<void> {
  await mkdir(dirname(destination), { recursive: true });

  try {
    if ((await checksum(destination, algorithm)) === expected) return;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
  }

  const temporary = `${destination}.part`;
  try {
    const response = await request(url);
    if (!response.body) {
      throw new Error("Download response has no body");
    }

    await pipeline(
      Readable.fromWeb(response.body),
      createWriteStream(temporary)
    );

    if ((await checksum(temporary, algorithm)) !== expected) {
      throw new Error(`Checksum mismatch: ${destination}`);
    }

    await rename(temporary, destination);
  } finally {
    await rm(temporary, { force: true }).catch(() => Promise.resolve(null));
  }
}

export interface PackSource {
  repository: string;
  release: string;
  clientAssetPattern: string;
}

interface ReleaseAsset {
  id: number;
  name: string;
  state: string;
  digest: string | null;
  browser_download_url: string;
}

interface GitHubRelease {
  id: number;
  tag_name: string;
  published_at: string;
  draft: boolean;
  prerelease: boolean;
  assets: ReleaseAsset[];
}

export async function getGithubRelease(source: PackSource) {
  const endpoint =
    source.release === "latest"
      ? "latest"
      : `tags/${encodeURIComponent(source.release)}`;

  const response = await request(
    `https://api.github.com/repos/${source.repository}/releases/${endpoint}`,
    {
      headers: {
        Accept: "application/vnd.github+json",
        ...(process.env.GITHUB_TOKEN
          ? { Authorization: `Bearer ${process.env.GITHUB_TOKEN}` }
          : {}),
      },
    }
  );

  const release = (await response.json()) as GitHubRelease;
  const pattern = new RegExp(source.clientAssetPattern, "i");

  const candidates = release.assets.filter(
    (asset) => asset.state === "uploaded" && pattern.test(asset.name)
  );
  if (candidates.length !== 1) {
    throw new Error(
      `Expected exactly one client ZIP in release ${release.tag_name}; found ${candidates.length}`
    );
  }

  const asset = candidates[0];
  if (!asset.digest || !/^sha256:[a-f0-9]{64}$/i.test(asset.digest)) {
    throw new Error(`GitHub provides no SHA-256 digest for ${asset.name}`);
  }

  return {
    repository: source.repository,
    tag: release.tag_name,
    release_id: release.id,
    published_at: release.published_at,
    asset: asset.name,
    asset_id: asset.id,
    sha256: asset.digest.slice("sha256:".length).toLowerCase(),
    download_url: asset.browser_download_url,
  };
}

function splitParallel<T>(array: T[], n: number) {
  const increment = Math.ceil(array.length / n);
  const result = [];
  let index = 0;
  for (let i = 0; i < n; i++) {
    const part = array.slice(index, index + increment);
    if (part.length) result.push(part);
    index += part.length;
  }
  return result;
}

export function runInParallel<T>(
  data: T[],
  count: number,
  callback: (value: T) => Promise<void>
) {
  return Promise.all(
    splitParallel(data, count).map(async (entries) => {
      for (const d of data) {
        await callback(d);
      }
    })
  );
}

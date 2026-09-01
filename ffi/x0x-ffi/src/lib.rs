//! C ABI for embedding x0x (the post-quantum agent gossip network) in-process
//! inside a mobile host application.
//!
//! The x0x daemon — the same REST + WebSocket API the `x0x` CLI talks to — is
//! started on an ephemeral loopback port inside the host process. The host
//! then drives it over HTTP on `127.0.0.1:<port>`, exactly as an external
//! daemon would be driven. See
//! <https://github.com/saorsa-labs/x0x#embedding-x0x-mobile--in-process>
//! for the embedding contract:
//!
//! * self-update is disabled on the embed path (an embedded library must not
//!   replace its host), and
//! * the host owns the filesystem — data and identity directories must be
//!   supplied explicitly; x0x never writes under `$HOME`.
//!
//! Kotlin consumers:
//! * Android loads `libx0x_ffi.so` through JNI (`X0xBridge` in the `shared`
//!   module's `androidMain` source set).
//! * iOS links `libx0x_ffi.a` through Kotlin/Native cinterop (`X0xBridge` in
//!   the `shared` module's `iosMain` source set).
//!
//! All functions are thread-safe. Handle values are opaque positive integers;
//! `0` is never a valid handle.

use std::cell::RefCell;
use std::collections::HashMap;
use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int, c_uint};
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Mutex, OnceLock};

use x0x::server::{serve, DaemonConfig, ServerHandle};

/// Handle as presented across the FFI boundary.
pub type Handle = u64;

/// A running embedded x0x daemon. Owns the tokio runtime the server runs on;
/// `shutdown_and_wait` (via [`x0x_ffi_shutdown`]) tears everything down.
struct Engine {
    runtime: tokio::runtime::Runtime,
    handle: ServerHandle,
    /// Durable API bearer token, cached at start (the host sends it as
    /// `Authorization: Bearer <token>` on every REST call).
    api_token: String,
}

fn engines() -> &'static Mutex<HashMap<Handle, Engine>> {
    static ENGINES: OnceLock<Mutex<HashMap<Handle, Engine>>> = OnceLock::new();
    ENGINES.get_or_init(|| Mutex::new(HashMap::new()))
}

fn next_handle() -> Handle {
    static NEXT: AtomicU64 = AtomicU64::new(1);
    NEXT.fetch_add(1, Ordering::Relaxed)
}

thread_local! {
    static LAST_ERROR: RefCell<Option<CString>> = const { RefCell::new(None) };
}

fn set_last_error(message: impl Into<String>) {
    let text = message.into();
    let sanitized = CString::new(text.replace('\0', " "))
        .unwrap_or_else(|_| CString::new("error message contained invalid bytes").unwrap());
    LAST_ERROR.with(|slot| {
        *slot.borrow_mut() = Some(sanitized);
    });
}

/// Record `err` as the thread-local last error and return `fallback`.
fn fail<T>(err: anyhow::Error, fallback: T) -> T {
    set_last_error(format!("{err:#}"));
    fallback
}

fn take_engine(handle: Handle) -> Option<Engine> {
    engines().lock().ok()?.remove(&handle)
}

fn with_engine<T>(handle: Handle, f: impl FnOnce(&Engine) -> T) -> Option<T> {
    let guard = engines().lock().ok()?;
    guard.get(&handle).map(f)
}

// ---------------------------------------------------------------------------
// Core engine lifecycle (plain C ABI — used directly on iOS via cinterop)
// ---------------------------------------------------------------------------

/// Start an embedded x0x daemon.
///
/// `data_dir` is the persistent state directory (sqlite stores, api token,
/// `api.port` …) and must be non-NULL and non-empty; `identity_dir` holds the
/// agent's ML-DSA-65 identity key (pass NULL to keep identity under
/// `data_dir`). Both directories are created if missing — the host owns their
/// placement.
///
/// Blocks only until the API listener is bound (x0x performs its fallible
/// startup — including first-run identity key generation — before the handle
/// is returned), so call from a background thread if that matters.
///
/// Returns a handle (> 0), or 0 on failure (see [`x0x_ffi_last_error`]).
/// On success the REST API serves on `127.0.0.1:`[`x0x_ffi_port`].
///
/// # Safety
///
/// `data_dir` and `identity_dir` (when non-NULL) must point to valid
/// NUL-terminated UTF-8 strings for the duration of the call; neither may
/// overlap the memory x0x writes (they are copied before any I/O).
#[no_mangle]
pub unsafe extern "C" fn x0x_ffi_start(data_dir: *const c_char, identity_dir: *const c_char) -> Handle {
    if data_dir.is_null() {
        set_last_error("data_dir must not be null");
        return 0;
    }
    // SAFETY: the caller guarantees a valid NUL-terminated string for the
    // duration of the call; we copy it into an owned `PathBuf` immediately.
    let data_dir = match unsafe { CStr::from_ptr(data_dir) }.to_str() {
        Ok(s) if !s.is_empty() => PathBuf::from(s),
        _ => {
            set_last_error("data_dir must be a non-empty UTF-8 path");
            return 0;
        }
    };
    let identity_dir = if identity_dir.is_null() {
        None
    } else {
        // SAFETY: as above; an empty or invalid string falls back to None
        // (identity kept under `data_dir`), never to `$HOME`.
        match unsafe { CStr::from_ptr(identity_dir) }.to_str() {
            Ok(s) if !s.is_empty() => Some(PathBuf::from(s)),
            _ => None,
        }
    };

    let mut config = DaemonConfig::default();
    // Ephemeral loopback port: the host app is the only intended client.
    config.api_address = "127.0.0.1:0".parse().expect("static address parses");
    config.identity_dir = identity_dir;
    config.data_dir = data_dir.clone();

    let runtime = match tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()
    {
        Ok(runtime) => runtime,
        Err(e) => return fail(anyhow::Error::new(e), 0),
    };

    // `serve` performs all fallible startup (dirs, identity, bind, api.port
    // write) and returns as soon as the server is accepting connections.
    let handle = match runtime.block_on(serve(config)) {
        Ok(handle) => handle,
        Err(e) => return fail(e, 0),
    };

    // The durable API token is generated/loaded during startup; cache it so
    // the host never has to know the on-disk layout.
    let api_token = match std::fs::read_to_string(data_dir.join("api-token")) {
        Ok(token) => token.trim().to_string(),
        Err(e) => return fail(anyhow::Error::new(e).context("reading api-token"), 0),
    };

    let id = next_handle();
    match engines().lock() {
        Ok(mut engines) => {
            engines.insert(
                id,
                Engine {
                    runtime,
                    handle,
                    api_token,
                },
            );
            id
        }
        Err(_) => {
            set_last_error("engine registry poisoned");
            0
        }
    }
}

/// The bound loopback port of the daemon's REST API.
///
/// Returns 0 if `handle` is unknown.
#[no_mangle]
pub extern "C" fn x0x_ffi_port(handle: Handle) -> c_int {
    with_engine(handle, |engine| engine.handle.local_addr().port() as c_int).unwrap_or(0)
}

/// Copy the durable API bearer token (NUL-terminated) into `buf`.
///
/// Returns the number of bytes needed to hold the token including the NUL
/// terminator — also when `buf` is NULL or `len` is too small, so the caller
/// can size the buffer and retry. When the return value is `<= len` the
/// buffer holds the full token. Returns `-1` for an unknown handle.
///
/// # Safety
///
/// `buf`, when non-NULL, must point to writable memory of at least `len`
/// bytes for the duration of the call.
#[no_mangle]
pub unsafe extern "C" fn x0x_ffi_api_token(handle: Handle, buf: *mut c_char, len: c_uint) -> c_int {
    let Some(token) = with_engine(handle, |engine| engine.api_token.clone()) else {
        set_last_error(format!("unknown handle {handle}"));
        return -1;
    };
    let needed = token.len() + 1;
    let len = len as usize;
    if !buf.is_null() && len > 0 {
        let bytes = token.as_bytes();
        let copy = bytes.len().min(len - 1);
        // SAFETY: `buf` points to at least `len` writable bytes; we write a
        // token prefix plus the NUL terminator, never more than `len` bytes.
        let dst = unsafe { std::slice::from_raw_parts_mut(buf.cast::<u8>(), len) };
        dst[..copy].copy_from_slice(&bytes[..copy]);
        dst[copy] = 0;
    }
    needed as c_int
}

/// Shut the daemon down gracefully and release the handle.
///
/// Stops the HTTP/SSE server, background tasks, the gossip runtime and the
/// QUIC endpoint, then drops the runtime. Blocks until completion.
///
/// Returns `0` on success, `-1` for an unknown handle, `-2` if the server
/// supervisor reported an error at shutdown.
#[no_mangle]
pub extern "C" fn x0x_ffi_shutdown(handle: Handle) -> c_int {
    let Some(engine) = take_engine(handle) else {
        set_last_error(format!("unknown handle {handle}"));
        return -1;
    };
    let Engine {
        runtime,
        handle,
        api_token: _,
    } = engine;
    match runtime.block_on(handle.shutdown_and_wait()) {
        Ok(()) => 0,
        Err(e) => fail(e, -2),
    }
}

/// The last error message recorded on this thread (NUL-terminated), or NULL.
/// Valid until the next FFI call on the same thread.
#[no_mangle]
pub extern "C" fn x0x_ffi_last_error() -> *const c_char {
    LAST_ERROR
        .with(|slot| slot.borrow().as_ref().map(|c| c.as_ptr()))
        .unwrap_or(std::ptr::null())
}

// ---------------------------------------------------------------------------
// JNI shims (Android). The Kotlin side is `X0xBridge` in the `shared` module.
// ---------------------------------------------------------------------------

#[cfg(target_os = "android")]
mod jni_shims {
    use super::*;
    use ::jni::objects::{JClass, JString};
    use ::jni::sys::{jint, jlong, jstring};
    use ::jni::JNIEnv;

    #[no_mangle]
    pub extern "system" fn Java_uk_veritaslabs_vesta_x0x_X0xBridge_nativeStart(
        mut env: JNIEnv,
        _class: JClass,
        data_dir: JString,
        identity_dir: JString,
    ) -> jlong {
        let data_dir = match env.get_string(&data_dir) {
            Ok(s) => String::from(s),
            Err(e) => {
                set_last_error(format!("data_dir: {e}"));
                return 0;
            }
        };
        let identity_dir = env
            .get_string(&identity_dir)
            .ok()
            .map(String::from)
            .filter(|s| !s.is_empty());
        let data_c = match CString::new(data_dir) {
            Ok(c) => c,
            Err(_) => {
                set_last_error("data_dir contained a NUL byte");
                return 0;
            }
        };
        let identity_c = identity_dir.and_then(|s| CString::new(s).ok());
        let identity_ptr = identity_c
            .as_deref()
            .map_or(std::ptr::null(), |c| c.as_ptr());
        // SAFETY: `data_c`/`identity_c` are owned CStrings alive for this
        // call; their pointers are valid NUL-terminated strings.
        let handle = unsafe { x0x_ffi_start(data_c.as_ptr(), identity_ptr) };
        handle as jlong
    }

    #[no_mangle]
    pub extern "system" fn Java_uk_veritaslabs_vesta_x0x_X0xBridge_nativePort(
        _env: JNIEnv,
        _class: JClass,
        handle: jlong,
    ) -> jint {
        x0x_ffi_port(handle as Handle)
    }

    #[no_mangle]
    pub extern "system" fn Java_uk_veritaslabs_vesta_x0x_X0xBridge_nativeApiToken(
        env: JNIEnv,
        _class: JClass,
        handle: jlong,
    ) -> jstring {
        let Some(token) = with_engine(handle as Handle, |engine| engine.api_token.clone()) else {
            set_last_error(format!("unknown handle {handle}"));
            return std::ptr::null_mut();
        };
        match env.new_string(token) {
            Ok(s) => s.into_raw(),
            Err(e) => {
                set_last_error(format!("new_string: {e}"));
                std::ptr::null_mut()
            }
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_uk_veritaslabs_vesta_x0x_X0xBridge_nativeShutdown(
        _env: JNIEnv,
        _class: JClass,
        handle: jlong,
    ) -> jint {
        x0x_ffi_shutdown(handle as Handle)
    }

    #[no_mangle]
    pub extern "system" fn Java_uk_veritaslabs_vesta_x0x_X0xBridge_nativeLastError(
        env: JNIEnv,
        _class: JClass,
    ) -> jstring {
        let message = LAST_ERROR.with(|slot| {
            slot.borrow()
                .as_ref()
                .map(|c| c.to_string_lossy().into_owned())
        });
        match message {
            Some(msg) => match env.new_string(msg) {
                Ok(s) => s.into_raw(),
                Err(_) => std::ptr::null_mut(),
            },
            None => std::ptr::null_mut(),
        }
    }
}

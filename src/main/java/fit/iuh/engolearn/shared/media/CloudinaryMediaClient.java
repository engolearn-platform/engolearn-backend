package fit.iuh.engolearn.shared.media;

import com.cloudinary.Cloudinary;

/** Shared Cloudinary client used by legacy Speaking and the Media feature. */
public record CloudinaryMediaClient(Cloudinary cloudinary, boolean configured) {
}

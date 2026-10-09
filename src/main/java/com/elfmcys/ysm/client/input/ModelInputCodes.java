package com.elfmcys.ysm.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

/** Adapts SDL physical input to the numeric keyboard/mouse arguments used by existing models. */
public final class ModelInputCodes {
    public static final int KEY_COUNT = 349;
    public static final int MOUSE_COUNT = 8;

    private ModelInputCodes() {
    }

    public static int keyboard(int key) {
        if (key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z) {
            return 65 + key - InputConstants.KEY_A;
        }
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
            return 49 + key - InputConstants.KEY_1;
        }
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
            return 290 + key - InputConstants.KEY_F1;
        }
        if (key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24) {
            return 302 + key - InputConstants.KEY_F13;
        }
        if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
            return 321 + key - InputConstants.KEY_NUMPAD1;
        }
        return switch (key) {
            case InputConstants.KEY_SPACE -> 32;
            case InputConstants.KEY_APOSTROPHE -> 39;
            case InputConstants.KEY_COMMA -> 44;
            case InputConstants.KEY_MINUS -> 45;
            case InputConstants.KEY_PERIOD -> 46;
            case InputConstants.KEY_SLASH -> 47;
            case InputConstants.KEY_0 -> 48;
            case InputConstants.KEY_SEMICOLON -> 59;
            case InputConstants.KEY_EQUALS -> 61;
            case InputConstants.KEY_LBRACKET -> 91;
            case InputConstants.KEY_BACKSLASH -> 92;
            case InputConstants.KEY_RBRACKET -> 93;
            case InputConstants.KEY_GRAVE -> 96;
            case InputConstants.KEY_ESCAPE -> 256;
            case InputConstants.KEY_RETURN -> 257;
            case InputConstants.KEY_TAB -> 258;
            case InputConstants.KEY_BACKSPACE -> 259;
            case InputConstants.KEY_INSERT -> 260;
            case InputConstants.KEY_DELETE -> 261;
            case InputConstants.KEY_RIGHT -> 262;
            case InputConstants.KEY_LEFT -> 263;
            case InputConstants.KEY_DOWN -> 264;
            case InputConstants.KEY_UP -> 265;
            case InputConstants.KEY_PAGEUP -> 266;
            case InputConstants.KEY_PAGEDOWN -> 267;
            case InputConstants.KEY_HOME -> 268;
            case InputConstants.KEY_END -> 269;
            case InputConstants.KEY_CAPSLOCK -> 280;
            case InputConstants.KEY_SCROLLLOCK -> 281;
            case InputConstants.KEY_NUMLOCK -> 282;
            case InputConstants.KEY_PRINTSCREEN -> 283;
            case InputConstants.KEY_PAUSE -> 284;
            case InputConstants.KEY_NUMPAD0 -> 320;
            case SDLScancode.SDL_SCANCODE_KP_PERIOD -> 330;
            case SDLScancode.SDL_SCANCODE_KP_DIVIDE -> 331;
            case InputConstants.KEY_MULTIPLY -> 332;
            case SDLScancode.SDL_SCANCODE_KP_MINUS -> 333;
            case InputConstants.KEY_ADD -> 334;
            case InputConstants.KEY_NUMPADENTER -> 335;
            case InputConstants.KEY_NUMPADEQUALS -> 336;
            case InputConstants.KEY_LSHIFT -> 340;
            case InputConstants.KEY_LCONTROL -> 341;
            case InputConstants.KEY_LALT -> 342;
            case InputConstants.KEY_LGUI -> 343;
            case InputConstants.KEY_RSHIFT -> 344;
            case InputConstants.KEY_RCONTROL -> 345;
            case InputConstants.KEY_RALT -> 346;
            case InputConstants.KEY_RGUI -> 347;
            case SDLScancode.SDL_SCANCODE_APPLICATION -> 348;
            default -> -1;
        };
    }

    public static int mouse(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            case InputConstants.MOUSE_BUTTON_MIDDLE -> 2;
            default -> button >= InputConstants.MOUSE_BUTTON_4 && button <= InputConstants.MOUSE_BUTTON_8 ? button - 1 : -1;
        };
    }
}

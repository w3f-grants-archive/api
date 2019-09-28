package org.polkadot.types.primitive.generic;

import org.apache.commons.lang3.ArrayUtils;
import org.polkadot.direct.IFunction;
import org.polkadot.types.Codec;
import org.polkadot.types.Types;
import org.polkadot.types.codec.Vector;
import org.polkadot.types.codec.*;
import org.polkadot.types.interfaces.metadata.Types.FunctionArgumentMetadataV1;
import org.polkadot.types.interfaces.metadata.Types.FunctionMetadataV7;
import org.polkadot.types.metadata.v0.Modules;
import org.polkadot.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @name Call
 * @description Extrinsic function descriptor, as defined in
 * {@link https://github.com/paritytech/wiki/blob/master/Extrinsic.md#the-extrinsic-format-for-node}.
 */
public class Call extends Struct implements Types.IMethod {

    private static final Logger logger = LoggerFactory.getLogger(Call.class);


    //const injected: { [index: string]: MethodFunction } = {};
    static final Map<String, CallFunction> INJECTED = new HashMap();

    static final CallFunction FN_UNKNOWN = new CallFunction() {
        @Override
        public Call apply(Object... args) {
            return null;
        }

        @Override
        public Object toJson() {
            return null;
        }
    };


    /**
     * @name CallIndex
     * @description A wrapper around the `[sectionIndex, methodIndex]` value that uniquely identifies a method
     */
    public static class CallIndex extends U8aFixed {
        public CallIndex(Object value) {
            super(value, 16);
        }
    }

    public static class DecodeMethodInput {

        public DecodeMethodInput(Object args, CallIndex callIndex) {
            this.args = args;
            this.callIndex = callIndex;
        }

        //args: any;
        //callIndex: MethodIndex | Uint8Array;
        public Object args;
        public CallIndex callIndex;

        public Object getArgs() {
            return args;
        }

        public CallIndex getCallIndex() {
            return callIndex;
        }
    }

    public static class DecodedMethod extends DecodeMethodInput {


        public DecodedMethod(Object args, CallIndex callIndex, Types.ConstructorDef argsDef, FunctionMetadataV7 meta) {
            super(args, callIndex);
            this.argsDef = argsDef;
            this.meta = meta;
        }

        public Types.ConstructorDef argsDef;
        public FunctionMetadataV7 meta;

        public Types.ConstructorDef getArgsDef() {
            return argsDef;
        }

        public FunctionMetadataV7 getMeta() {
            return meta;
        }
    }

    //  interface MethodFunction {
    //(...args: any[]): Method;
    //      callIndex: Uint8Array;
    //      meta: FunctionMetadata;
    //      method: string;
    //      section: string;
    //      toJSON: () => any;
    //  }
    public abstract static class CallFunction implements IFunction {

        public abstract Call apply(Object... args);

        byte[] callIndex;
        FunctionMetadataV7 meta;
        String method;
        String section;

        public abstract Object toJson();

        public byte[] getCallIndex() {
            return callIndex;
        }

        public void setCallIndex(byte[] callIndex) {
            this.callIndex = callIndex;
        }

        public FunctionMetadataV7 getMeta() {
            return meta;
        }

        public void setMeta(FunctionMetadataV7 meta) {
            this.meta = meta;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getSection() {
            return section;
        }

        public void setSection(String section) {
            this.section = section;
        }
    }

    //  export interface Methods {
    //[key: string]: MethodFunction;
    //  }
    public static class Methods extends LinkedHashMap<String, CallFunction> {

    }

    //
    //  export interface ModulesWithMethods {
    //[key: string]: Methods; // Will hold modules returned by state_getMetadata
    //  }

    public static class ModulesWithMethods extends LinkedHashMap<String, Methods> {

    }

    protected FunctionMetadataV7 meta;

    public Call(Object value, FunctionMetadataV7 meta) {
        super(new Types.ConstructorDef()
                        .add("callIndex", CallIndex.class)
                        .add("args", Struct.with(decodeMethod(value, meta).argsDef))
                , decodeMethod(value, meta));
        this.meta = decodeMethod(value, meta).meta;
    }

    /**
     * Decode input to pass into constructor.
     *
     * @param value - Value to decode, one of:
     *              - hex
     *              - Uint8Array
     *              - @see DecodeMethodInput
     * @param meta - Metadata to use, so that `injectMethods` lookup is not
     *              necessary.
     */
    //private static decodeMethod (value: DecodedMethod | Uint8Array | string, _meta?: FunctionMetadata): DecodedMethod {
    private static DecodedMethod decodeMethod(Object value, FunctionMetadataV7 meta) {
        if (Utils.isHex(value)) {
            return decodeMethod(Utils.hexToU8a((String) value), meta);
        } else if (Utils.isU8a(value)) {
            byte[] u8a = Utils.u8aToU8a(value);
            //U8a u8a = (U8a) value;
            // The first 2 bytes are the callIndex
            byte[] callIndex = ArrayUtils.subarray(u8a, 0, 2);
            //U8a callIndex = u8a.subarray(0, 2);

            // Find metadata with callIndex
            FunctionMetadataV7 fMeta = meta;
            if (fMeta == null) {
                fMeta = findFunction(callIndex).meta;
            }

            return new DecodedMethod(
                    ArrayUtils.subarray(u8a, 2, u8a.length),
                    new CallIndex(callIndex),
                    getArgsDef(fMeta),
                    fMeta
            );
            //} else if (isObject(value) && value.callIndex && value.args) {
            //} else if (value instanceof Struct) {
        } else if (value instanceof Map || value instanceof Types.IMethod) {
            Object args = null;
            CallIndex callIndex = null;
            if (value instanceof Types.IMethod) {
                //value instanceof Types.IMethod
                args = ((Types.IMethod) value).getArgs();
                callIndex = new CallIndex(((Types.IMethod) value).getCallIndex());
            } else {
                Map struct = (Map) value;
                // destructure value, we only pass args/methodsIndex out
                args = struct.get("args");
                callIndex = new CallIndex(struct.get("callIndex"));
            }

            // Get the correct lookupIndex
            byte[] lookupIndex = null;
            if (callIndex instanceof CallIndex) {
                lookupIndex = callIndex.toU8a();
            } else {
                lookupIndex = Utils.u8aToU8a(callIndex);
            }

            // Find metadata with callIndex
            FunctionMetadataV7 fMeta = meta;
            if (fMeta == null) {
                fMeta = findFunction(lookupIndex).meta;
            }

            return new DecodedMethod(
                    args,
                    callIndex,
                    getArgsDef(fMeta),
                    fMeta
            );
        }

        logger.error("Method: cannot decode value {} of type {}", value, value.getClass());
        throw new RuntimeException("Call: Cannot decode value " + value + " of type " + value.getClass());

        //return new DecodedMethod(
        //        new U8a(new byte[0]),
        //        new CallIndex(new byte[]{UnsignedBytes.MAX_VALUE, UnsignedBytes.MAX_VALUE}),
        //        new Types.ConstructorDef(),
        //        new FunctionMetadataV7(null)
        //);
    }


    // We could only inject the meta (see injectMethods below) and then do a
    // meta-only lookup via
    //
    //   metadata.modules[callIndex[0]].module.call.functions[callIndex[1]]
    //
    // As a convenience helper though, we return the full constructor function,
    // which includes the meta, name, section & actual interface for calling
    static CallFunction findFunction(byte[] callIndex) {
        //assert(Object.keys(injected).length > 0, 'Calling findFunction before extrinsics have been injected.');
        return INJECTED.getOrDefault(Arrays.toString(callIndex), FN_UNKNOWN);
    }

    /**
     * Get a mapping of `argument name -> argument type` for the function, from
     * its metadata.
     *
     * @param meta - The function metadata used to get the definition.
     */
    private static Types.ConstructorDef getArgsDef(FunctionMetadataV7 meta) {
        Types.ConstructorDef constructorDef = new Types.ConstructorDef();
        filterOrigin(meta).stream().forEach((argumentMetadata) -> {
            Types.ConstructorCodec type = CreateType.getTypeClass(CreateType.getTypeDef(argumentMetadata.type.toString(), null));
            constructorDef.add(argumentMetadata.name.toString(), type);
        });
        return constructorDef;
    }

    // If the extrinsic function has an argument of type `Origin`, we ignore it
    public static List<FunctionArgumentMetadataV1> filterOrigin(FunctionMetadataV7 meta) {
        // FIXME should be `arg.type !== Origin`, but doesn't work...
        if (meta != null) {
            Vec<FunctionArgumentMetadataV1> arguments = meta.args;
            List<FunctionArgumentMetadataV1> ret = arguments.stream()
                    .filter((FunctionArgumentMetadataV1 argument) -> !argument.type.toString().equals("Origin"))
                    .collect(Collectors.toList());
            return ret;
        }
        return Collections.emptyList();
    }


    // This is called/injected by the API on init, allowing a snapshot of
    // the available system extrinsics to be used in lookups
    public static void injectMethods(ModulesWithMethods moduleMethods) {
        moduleMethods.forEach((k, v) -> {
            v.forEach((ik, iv) -> INJECTED.put(Arrays.toString(iv.callIndex), iv));
        });
    }


    /**
     * The arguments for the function call
     */
    @Override
    public List<Codec> getArgs() {
        // FIXME This should return a Struct instead of an Array
        Struct args = this.getField("args");
        return args.values().stream().collect(Collectors.toList());
    }

    @Override
    public Types.ConstructorDef getArgsDef() {
        return getArgsDef(this.meta);
    }

    /**
     * The encoded `[sectionIndex, methodIndex]` identifier
     */
    @Override
    public byte[] getCallIndex() {
        CallIndex callIndex = this.getField("callIndex");
        return callIndex.toU8a(false);
    }

    /**
     * The encoded data
     */
    @Override
    public byte[] getData() {
        Struct args = this.getField("args");
        return args.toU8a(false);
    }


    /**
     * `true` if the `Origin` type is on the method (extrinsic method)
     */
    @Override
    public boolean hasOrigin() {
        Vector<Modules.FunctionArgumentMetadata> arguments = this.meta.getField("arguments");
        Modules.FunctionArgumentMetadata firstArg = arguments.size() > 0 ? arguments.get(0) : null;

        return firstArg != null && firstArg.getType().toString().equals("Origin");
    }

    /**
     * The FunctionMetadata
     */
    @Override
    public FunctionMetadataV7 getMeta() {
        return this.meta;
    }


    /**
     * @description Returns the base runtime type name for this instance
     */
    @Override
    public String toRawType() {
        return "Method";
    }
}
/**
 * Profile Management Screen
 * Complete profile editor with all fields from Emergency ID Sheet
 */

import { File, Paths } from 'expo-file-system';
import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import DateTimePicker, { DateTimePickerEvent } from '@react-native-community/datetimepicker';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';

import { ScreenHeader } from '@/components/ScreenHeader';
import { ThemedText } from '@/components/ThemedText';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors } from '@/constants/Colors';
import { Spacing, Radius } from '@/constants/Spacing';
import { Typography } from '@/constants/Typography';
import { useProfile } from '@/context/ProfileContext';
import { useTheme } from '@/context/ThemeContext';
import { useUnsavedChangesGuard } from '@/hooks/useUnsavedChangesGuard';
import { formatPhoneInput } from '@/utils/phone';

type SectionKey = 'personal' | 'medical' | 'communication' | 'devices';

const MOBILITY_OPTIONS = [
  'Walks independently',
  'Uses cane',
  'Uses walker',
  'Manual wheelchair',
  'Motorized wheelchair',
  'Mobility scooter',
  'Bicycle',
  'Has vehicle',
  'Other',
] as const;

const WEB_CAMERA_UNAVAILABLE_MESSAGE =
  'Taking a photo is only available in the mobile app. Please use "Choose Photo" on web.';

type MobilityOption = (typeof MOBILITY_OPTIONS)[number];

export default function ProfileScreen() {
  const router = useRouter();
  const navigation = useNavigation();
  const { profile, saveProfile, isLoading, refreshProfile } = useProfile();
  const { colorScheme } = useTheme();
  const theme = Colors[colorScheme];

  const [expandedSection, setExpandedSection] = useState<SectionKey | null>('personal');
  const [isSaving, setIsSaving] = useState(false);
  const [showDatePicker, setShowDatePicker] = useState(false);
  const [showIosDatePicker, setShowIosDatePicker] = useState(false);
  const [pendingDobDate, setPendingDobDate] = useState<Date>(new Date(1940, 0, 1));
  const [initialSnapshot, setInitialSnapshot] = useState('');
  // When mobilityLevel is set to a custom value not in MOBILITY_OPTIONS, we show "Other" selected
  // and store the custom text in mobilityOtherText
  const [mobilityOtherText, setMobilityOtherText] = useState('');
  const [selectedMobilityOptions, setSelectedMobilityOptions] = useState<MobilityOption[]>([]);

  // Form state
  const [form, setForm] = useState({
    // Personal Info
    name: '',
    nickname: '',
    dateOfBirth: '',
    photoUri: '',
    height: '',
    weight: '',
    hairColor: '',
    eyeColor: '',
    identifyingMarks: '',

    // Medical & Behavioral
    medicalConditions: '',
    medications: '',
    allergies: '',
    cognitiveStatus: '',
    dominantHand: 'unknown' as 'left' | 'right' | 'unknown',

    // Communication & De-escalation
    communicationPreference: '',
    escalationSigns: '',
    deescalationTechniques: '',
    approachGuidance: '',
    likes: '',
    dislikesTriggers: '',
    safeWord: '',

    // Devices & IDs
    locativeDeviceInfo: '',
    idBracelets: '',
    medicAlertId: '',
    medicAlertHotline: '',
  });

  // Refresh profile when screen mounts (in case data was added during onboarding)
  useEffect(() => {
    refreshProfile();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Load profile data into form
  useEffect(() => {
    if (profile) {
      const savedMobility = profile.mobilityLevel || '';
      const tokens = savedMobility
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean);
      const knownSelections = tokens.filter((token) =>
        MOBILITY_OPTIONS.includes(token as MobilityOption),
      ) as MobilityOption[];
      const customSelections = tokens.filter(
        (token) => !MOBILITY_OPTIONS.includes(token as MobilityOption),
      );
      const hasCustomMobility = customSelections.length > 0;
      setForm({
        name: profile.name || '',
        nickname: profile.nickname || '',
        dateOfBirth: profile.dateOfBirth || '',
        photoUri: profile.photoUri || '',
        height: profile.height || '',
        weight: profile.weight || '',
        hairColor: profile.hairColor || '',
        eyeColor: profile.eyeColor || '',
        identifyingMarks: profile.identifyingMarks || '',
        medicalConditions: profile.medicalConditions || '',
        medications: profile.medications || '',
        allergies: profile.allergies || '',
        cognitiveStatus: profile.cognitiveStatus || '',
        dominantHand: profile.dominantHand || 'unknown',
        communicationPreference: profile.communicationPreference || '',
        escalationSigns: profile.escalationSigns || '',
        deescalationTechniques: profile.deescalationTechniques || '',
        approachGuidance: profile.approachGuidance || '',
        likes: profile.likes || '',
        dislikesTriggers: profile.dislikesTriggers || '',
        safeWord: profile.safeWord || '',
        locativeDeviceInfo: profile.locativeDeviceInfo || '',
        idBracelets: profile.idBracelets || '',
        medicAlertId: profile.medicAlertId || '',
        medicAlertHotline: profile.medicAlertHotline || '',
      });
      setSelectedMobilityOptions(
        hasCustomMobility ? [...knownSelections, 'Other'] : knownSelections,
      );
      setMobilityOtherText(customSelections.join(', '));
    }
  }, [profile]);

  const updateField = (field: keyof typeof form, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const formatDate = (date: Date): string => {
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const year = String(date.getFullYear());
    return `${month}/${day}/${year}`;
  };

  const parseDate = (value: string): Date | null => {
    const match = value.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
    if (!match) return null;

    const month = Number(match[1]);
    const day = Number(match[2]);
    const year = Number(match[3]);
    const parsed = new Date(year, month - 1, day);

    if (
      Number.isNaN(parsed.getTime()) ||
      parsed.getFullYear() !== year ||
      parsed.getMonth() !== month - 1 ||
      parsed.getDate() !== day
    ) {
      return null;
    }

    return parsed;
  };

  const formatDobInput = (value: string): string => {
    const digits = value.replace(/\D/g, '').slice(0, 8);
    if (digits.length <= 2) return digits;
    if (digits.length <= 4) return `${digits.slice(0, 2)}/${digits.slice(2)}`;
    return `${digits.slice(0, 2)}/${digits.slice(2, 4)}/${digits.slice(4)}`;
  };

  const openDatePicker = () => {
    const initialDate = parseDate(form.dateOfBirth) ?? new Date(1940, 0, 1);

    if (Platform.OS === 'ios') {
      setPendingDobDate(initialDate);
      setShowIosDatePicker(true);
      return;
    }

    setShowDatePicker(true);
  };

  const onAndroidDateChange = (event: DateTimePickerEvent, selectedDate?: Date) => {
    setShowDatePicker(false);

    if (event.type === 'dismissed' || !selectedDate) {
      return;
    }

    updateField('dateOfBirth', formatDate(selectedDate));
  };

  const onIosDateChange = (_event: DateTimePickerEvent, selectedDate?: Date) => {
    if (!selectedDate) return;
    setPendingDobDate(selectedDate);
  };

  const applyIosDate = () => {
    updateField('dateOfBirth', formatDate(pendingDobDate));
    setShowIosDatePicker(false);
  };

  const formatHeightInput = (value: string): string => {
    const digits = value.replace(/\D/g, '').slice(0, 3);
    if (!digits) return '';
    if (digits.length === 1) return digits;
    if (digits.length === 2) return `${digits[0]}'${digits[1]}"`;
    return `${digits[0]}'${digits.slice(1)}"`;
  };

  const formatWeightInput = (value: string): string => {
    const digits = value.replace(/\D/g, '').slice(0, 4);
    if (!digits) return '';
    return digits.length > 3 ? `${digits.slice(0, -3)},${digits.slice(-3)}` : digits;
  };

  const formatMedicAlertIdInput = (value: string): string => {
    const chars = value
      .replace(/[^a-zA-Z0-9]/g, '')
      .toUpperCase()
      .slice(0, 16);
    if (!chars) return '';

    const chunks = chars.match(/.{1,4}/g);
    return chunks ? chunks.join('-') : chars;
  };

  const formatFieldInput = (field: keyof typeof form, value: string): string => {
    if (field === 'height') return formatHeightInput(value);
    if (field === 'weight') return formatWeightInput(value);
    if (field === 'medicAlertId') return formatMedicAlertIdInput(value);
    if (field === 'medicAlertHotline') return formatPhoneInput(value);
    return value;
  };

  const toggleMobilityOption = (option: MobilityOption) => {
    setSelectedMobilityOptions((prev) => {
      const isSelected = prev.includes(option);

      if (isSelected) {
        if (option === 'Other') {
          setMobilityOtherText('');
        }
        return prev.filter((item) => item !== option);
      }

      return [...prev, option];
    });
  };

  const getResolvedMobility = useCallback(() => {
    const mobilitySelections = selectedMobilityOptions.filter((item) => item !== 'Other');
    const hasOtherSelected = selectedMobilityOptions.includes('Other');
    const resolvedMobilityParts = [
      ...mobilitySelections,
      ...(hasOtherSelected && mobilityOtherText.trim() ? [mobilityOtherText.trim()] : []),
    ];
    return resolvedMobilityParts.length > 0 ? resolvedMobilityParts.join(', ') : '';
  }, [selectedMobilityOptions, mobilityOtherText]);

  const currentSnapshot = useMemo(
    () =>
      JSON.stringify({
        ...form,
        mobilityLevel: getResolvedMobility(),
      }),
    [form, getResolvedMobility],
  );

  const hasUnsavedChanges = initialSnapshot !== '' && currentSnapshot !== initialSnapshot;

  useEffect(() => {
    if (!isLoading && initialSnapshot === '') {
      setInitialSnapshot(currentSnapshot);
    }
  }, [isLoading, initialSnapshot, currentSnapshot]);

  useUnsavedChangesGuard({
    navigation,
    hasUnsavedChanges,
    isSaving,
    title: 'Unsaved Changes',
    message: 'You have unsaved changes. If you leave now, your edits will be lost.',
    confirmLabel: 'Discard Changes',
  });

  const handleSave = async () => {
    setIsSaving(true);
    const resolvedMobility = getResolvedMobility() || undefined;
    try {
      await saveProfile({
        name: form.name.trim() || undefined,
        nickname: form.nickname.trim() || undefined,
        dateOfBirth: form.dateOfBirth.trim() || undefined,
        photoUri: form.photoUri || undefined,
        height: form.height.trim() || undefined,
        weight: form.weight.trim() || undefined,
        hairColor: form.hairColor.trim() || undefined,
        eyeColor: form.eyeColor.trim() || undefined,
        identifyingMarks: form.identifyingMarks.trim() || undefined,
        medicalConditions: form.medicalConditions.trim() || undefined,
        medications: form.medications.trim() || undefined,
        allergies: form.allergies.trim() || undefined,
        cognitiveStatus: form.cognitiveStatus.trim() || undefined,
        dominantHand: form.dominantHand,
        mobilityLevel: resolvedMobility,
        communicationPreference: form.communicationPreference.trim() || undefined,
        escalationSigns: form.escalationSigns.trim() || undefined,
        deescalationTechniques: form.deescalationTechniques.trim() || undefined,
        approachGuidance: form.approachGuidance.trim() || undefined,
        likes: form.likes.trim() || undefined,
        dislikesTriggers: form.dislikesTriggers.trim() || undefined,
        safeWord: form.safeWord.trim() || undefined,
        locativeDeviceInfo: form.locativeDeviceInfo.trim() || undefined,
        idBracelets: form.idBracelets.trim() || undefined,
        medicAlertId: form.medicAlertId.trim() || undefined,
        medicAlertHotline: form.medicAlertHotline.trim() || undefined,
      });
      setInitialSnapshot(currentSnapshot);
      router.replace('/(tabs)');
    } catch (err) {
      console.error('Error saving profile:', err);
    } finally {
      setIsSaving(false);
    }
  };

  const pickImage = async () => {
    const { status } = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (status !== 'granted') {
      alert('We need photo library access to update the photo.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.8,
    });

    if (!result.canceled && result.assets[0]) {
      await savePhotoLocally(result.assets[0].uri);
    }
  };

  const takePhoto = async () => {
    if (Platform.OS === 'web') {
      Alert.alert('Camera Not Available', WEB_CAMERA_UNAVAILABLE_MESSAGE);
      return;
    }

    const { status } = await ImagePicker.requestCameraPermissionsAsync();
    if (status !== 'granted') {
      alert('We need camera access to take a photo.');
      return;
    }

    const result = await ImagePicker.launchCameraAsync({
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.8,
    });

    if (!result.canceled && result.assets[0]) {
      await savePhotoLocally(result.assets[0].uri);
    }
  };

  const savePhotoLocally = async (uri: string) => {
    try {
      if (Platform.OS === 'web') {
        setForm((prev) => ({ ...prev, photoUri: uri }));
        return;
      }

      const filename = `profile_photo_${Date.now()}.jpg`;
      const sourceFile = new File(uri);
      const destFile = new File(Paths.document, filename);

      await sourceFile.copy(destFile);

      setForm((prev) => ({ ...prev, photoUri: destFile.uri }));
    } catch (error) {
      console.error('Error saving photo:', error);
    }
  };

  const toggleSection = (section: SectionKey) => {
    setExpandedSection((prev) => (prev === section ? null : section));
  };

  if (isLoading) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.tint} />
        </View>
      </SafeAreaView>
    );
  }

  const renderInput = (
    label: string,
    field: keyof typeof form,
    options?: {
      placeholder?: string;
      multiline?: boolean;
      keyboardType?: 'default' | 'phone-pad' | 'email-address' | 'number-pad';
      hint?: string;
    },
  ) => (
    <View style={styles.inputGroup}>
      <ThemedText style={[styles.label, { color: theme.text }]}>{label}</ThemedText>
      {options?.hint && (
        <ThemedText style={[styles.hint, { color: theme.textSecondary }]}>
          {options.hint}
        </ThemedText>
      )}
      <TextInput
        style={[
          styles.input,
          !options?.multiline && styles.inputSingleLine,
          options?.multiline && styles.textArea,
          {
            backgroundColor: theme.card,
            borderColor: theme.border,
            color: theme.text,
          },
        ]}
        value={form[field] as string}
        onChangeText={(v) => updateField(field, formatFieldInput(field, v))}
        placeholder={options?.placeholder}
        placeholderTextColor={theme.inputPlaceholder}
        multiline={options?.multiline}
        numberOfLines={options?.multiline ? 3 : 1}
        keyboardType={options?.keyboardType || 'default'}
      />
    </View>
  );

  const renderSection = (
    key: SectionKey,
    title: string,
    icon: string,
    content: React.ReactNode,
  ) => (
    <View style={[styles.section, { backgroundColor: theme.card, borderColor: theme.border }]}>
      <Pressable style={styles.sectionHeader} onPress={() => toggleSection(key)}>
        <View style={[styles.sectionIconWrap, { backgroundColor: theme.primaryLight }]}>
          <IconSymbol name={icon as any} size={16} color={theme.primary} />
        </View>
        <ThemedText type="bodyBold" style={[styles.sectionTitle, { color: theme.text }]}>
          {title}
        </ThemedText>
        <IconSymbol
          name={expandedSection === key ? 'chevron.up' : 'chevron.down'}
          size={16}
          color={theme.textSecondary}
        />
      </Pressable>
      {expandedSection === key && <View style={styles.sectionContent}>{content}</View>}
    </View>
  );

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.background }]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardView}
      >
        <ScreenHeader
          title="Edit Profile"
          rightElement={
            <Pressable
              onPress={handleSave}
              style={[styles.saveButton, { backgroundColor: theme.tint }]}
              disabled={isSaving}
            >
              <ThemedText style={styles.saveText} numberOfLines={1}>
                {isSaving ? 'Saving...' : 'Save'}
              </ThemedText>
            </Pressable>
          }
        />

        <ScrollView style={styles.scrollView} contentContainerStyle={styles.scrollContent}>
          {/* Photo Section */}
          <View style={styles.photoSection}>
            <View style={styles.photoContainer}>
              {form.photoUri ? (
                <Image source={{ uri: form.photoUri }} style={styles.photo} contentFit="cover" />
              ) : (
                <View
                  style={[
                    styles.photoPlaceholder,
                    { backgroundColor: theme.card, borderColor: theme.border },
                  ]}
                >
                  <ThemedText style={styles.photoPlaceholderText}>📷</ThemedText>
                </View>
              )}
            </View>
            <View style={styles.photoButtons}>
              <Pressable
                style={[styles.photoButton, { backgroundColor: theme.tint }]}
                onPress={takePhoto}
              >
                <ThemedText style={styles.photoButtonText}>Take Photo</ThemedText>
              </Pressable>
              <Pressable
                style={[styles.photoButton, { backgroundColor: theme.primary }]}
                onPress={pickImage}
              >
                <ThemedText style={styles.photoButtonText}>Choose Photo</ThemedText>
              </Pressable>
            </View>
          </View>

          {/* Personal Info Section */}
          {renderSection(
            'personal',
            'Personal Information',
            'person.fill',
            <>
              {renderInput('Name', 'name', { placeholder: 'Full name (required)' })}
              {renderInput('Nickname / Preferred Name', 'nickname', {
                placeholder: 'What they prefer to be called',
              })}
              <View style={styles.inputGroup}>
                <ThemedText style={[styles.label, { color: theme.text }]}>Date of Birth</ThemedText>
                <View
                  style={[
                    styles.dateInputContainer,
                    {
                      backgroundColor: theme.card,
                      borderColor: theme.border,
                    },
                  ]}
                >
                  <TextInput
                    style={[
                      styles.dateInput,
                      {
                        color: theme.text,
                      },
                    ]}
                    value={form.dateOfBirth}
                    onChangeText={(v) => updateField('dateOfBirth', formatDobInput(v))}
                    placeholder="MM/DD/YYYY"
                    placeholderTextColor={theme.inputPlaceholder}
                    keyboardType="number-pad"
                    maxLength={10}
                  />
                  <Pressable onPress={openDatePicker} style={styles.calendarHint} hitSlop={8}>
                    <IconSymbol name="calendar" size={18} color={theme.textSecondary} />
                  </Pressable>
                </View>

                {showDatePicker && Platform.OS !== 'ios' && (
                  <DateTimePicker
                    value={parseDate(form.dateOfBirth) ?? new Date(1940, 0, 1)}
                    mode="date"
                    display="default"
                    maximumDate={new Date()}
                    onChange={onAndroidDateChange}
                  />
                )}
              </View>

              <View style={styles.row}>
                <View style={styles.halfWidth}>
                  {renderInput('Height', 'height', {
                    placeholder: '5\'6"',
                    keyboardType: 'number-pad',
                  })}
                </View>
                <View style={styles.halfWidth}>
                  {renderInput('Weight', 'weight', {
                    placeholder: '150 lbs',
                    keyboardType: 'number-pad',
                  })}
                </View>
              </View>

              <View style={styles.row}>
                <View style={styles.halfWidth}>
                  {renderInput('Hair Color', 'hairColor', { placeholder: 'Gray, short' })}
                </View>
                <View style={styles.halfWidth}>
                  {renderInput('Eye Color', 'eyeColor', { placeholder: 'Blue' })}
                </View>
              </View>

              {renderInput('Identifying Marks', 'identifyingMarks', {
                placeholder: 'Tattoos, scars, birthmarks, glasses, hearing aids...',
                multiline: true,
                hint: 'Anything that would help identify them',
              })}
            </>,
          )}

          {/* Medical & Behavioral Section */}
          {renderSection(
            'medical',
            'Medical & Behavioral',
            'cross.fill',
            <>
              {renderInput('Medical Conditions', 'medicalConditions', {
                placeholder: "Alzheimer's, diabetes, heart condition...",
                multiline: true,
              })}
              {renderInput('Current Medications', 'medications', {
                placeholder: 'List all current medications',
                multiline: true,
              })}
              {renderInput('Allergies', 'allergies', {
                placeholder: 'Drug allergies, food allergies, environmental...',
                multiline: true,
              })}
              {renderInput('Cognitive Status', 'cognitiveStatus', {
                placeholder: 'Moderate dementia; may be disoriented; responds to first name',
                multiline: true,
                hint: 'How do they typically present? What should responders expect?',
              })}

              <View style={styles.inputGroup}>
                <ThemedText style={[styles.label, { color: theme.text }]}>Dominant Hand</ThemedText>
                <ThemedText style={[styles.hint, { color: theme.textSecondary }]}>
                  People often veer in the direction of their dominant hand when lost
                </ThemedText>
                <View style={styles.buttonGroup}>
                  {(['left', 'right', 'unknown'] as const).map((hand) => (
                    <Pressable
                      key={hand}
                      style={[
                        styles.optionButton,
                        { borderColor: theme.border },
                        form.dominantHand === hand && {
                          backgroundColor: theme.tint,
                          borderColor: theme.tint,
                        },
                      ]}
                      onPress={() => updateField('dominantHand', hand)}
                    >
                      <ThemedText
                        style={[styles.optionText, form.dominantHand === hand && { color: '#fff' }]}
                      >
                        {hand === 'left' ? '← Left' : hand === 'right' ? 'Right →' : 'Unknown'}
                      </ThemedText>
                    </Pressable>
                  ))}
                </View>
              </View>

              <View style={styles.inputGroup}>
                <ThemedText style={[styles.label, { color: theme.text }]}>
                  Mobility Level
                </ThemedText>
                <View style={styles.chipGroup}>
                  {MOBILITY_OPTIONS.map((option) => (
                    <Pressable
                      key={option}
                      style={[
                        styles.chip,
                        { borderColor: theme.border },
                        selectedMobilityOptions.includes(option) && {
                          backgroundColor: theme.tint,
                          borderColor: theme.tint,
                        },
                      ]}
                      onPress={() => toggleMobilityOption(option)}
                    >
                      <ThemedText
                        style={[
                          styles.chipText,
                          selectedMobilityOptions.includes(option) && { color: '#fff' },
                        ]}
                      >
                        {option}
                      </ThemedText>
                    </Pressable>
                  ))}
                </View>
                {selectedMobilityOptions.includes('Other') && (
                  <TextInput
                    style={[
                      styles.input,
                      {
                        backgroundColor: theme.card,
                        borderColor: theme.border,
                        color: theme.text,
                        marginTop: Spacing.sm,
                      },
                    ]}
                    value={mobilityOtherText}
                    onChangeText={setMobilityOtherText}
                    placeholder="Describe mobility level..."
                    placeholderTextColor={theme.inputPlaceholder}
                  />
                )}
              </View>
            </>,
          )}

          {/* Communication & De-escalation Section */}
          {renderSection(
            'communication',
            'Communication & De-escalation',
            'bubble.left.fill',
            <>
              {renderInput('Communication Preference', 'communicationPreference', {
                placeholder: 'Speaking, non-speaking, uses gestures, sign language...',
                multiline: true,
              })}
              {renderInput('What Escalation Looks Like', 'escalationSigns', {
                placeholder: 'Crying, running, rocking, aggression, shutting down...',
                multiline: true,
                hint: 'Signs that they are becoming distressed',
              })}
              {renderInput('De-escalation Techniques', 'deescalationTechniques', {
                placeholder: 'Calm voice, short sentences, favorite song, show family photo...',
                multiline: true,
                hint: 'What has helped calm them in the past?',
              })}
              {renderInput('Best Way to Approach', 'approachGuidance', {
                placeholder: 'Approach slowly from the front, introduce yourself, keep distance...',
                multiline: true,
              })}
              {renderInput('Likes / Comforts', 'likes', {
                placeholder: 'Favorite music, TV shows, toys, foods, activities...',
                multiline: true,
                hint: 'What makes them feel safe or happy?',
              })}
              {renderInput('Dislikes / Triggers', 'dislikesTriggers', {
                placeholder: 'Loud noises, crowds, being touched, flashing lights...',
                multiline: true,
                hint: 'What should be avoided?',
              })}
              {renderInput('Family Safe Word', 'safeWord', {
                placeholder: 'A word or phrase that indicates you are a safe person',
                hint: 'Something only family would know',
              })}
            </>,
          )}

          {/* Devices & IDs Section */}
          {renderSection(
            'devices',
            'Devices & IDs',
            'location.fill',
            <>
              {renderInput('Personal Locator Device', 'locativeDeviceInfo', {
                placeholder: 'Personal locator (e.g., AirTag on keys/bag, watch beacon)',
                multiline: true,
                hint: 'Optional: only include devices they already use',
              })}
              {renderInput('ID Bracelets', 'idBracelets', {
                placeholder: 'MedicAlert, Project Lifesaver, RoadID...',
                hint: 'Medical or identification jewelry',
              })}
              {renderInput('MedicAlert Member ID', 'medicAlertId', {
                placeholder: 'Member ID number',
              })}
              {renderInput('MedicAlert Hotline', 'medicAlertHotline', {
                placeholder: '1-800-...',
                keyboardType: 'phone-pad',
              })}
            </>,
          )}

          {/* Bottom padding */}
          <View style={{ height: 40 }} />
        </ScrollView>

        <Modal
          visible={showIosDatePicker}
          transparent
          animationType="fade"
          onRequestClose={() => setShowIosDatePicker(false)}
        >
          <View style={styles.dateModalBackdrop}>
            <View
              style={[
                styles.dateModalSheet,
                { backgroundColor: theme.card, borderColor: theme.border },
              ]}
            >
              <ThemedText style={[styles.dateModalTitle, { color: theme.text }]}>
                Date of Birth
              </ThemedText>
              <DateTimePicker
                value={pendingDobDate}
                mode="date"
                display="spinner"
                maximumDate={new Date()}
                onChange={onIosDateChange}
              />
              <View style={styles.dateModalActions}>
                <Pressable
                  onPress={() => setShowIosDatePicker(false)}
                  style={[
                    styles.dateModalButton,
                    styles.dateModalButtonSecondary,
                    { borderColor: theme.border },
                  ]}
                >
                  <ThemedText style={[styles.dateModalButtonText, { color: theme.textSecondary }]}>
                    Cancel
                  </ThemedText>
                </Pressable>
                <Pressable
                  onPress={applyIosDate}
                  style={[styles.dateModalButton, { backgroundColor: theme.tint }]}
                >
                  <ThemedText style={[styles.dateModalButtonText, { color: '#fff' }]}>
                    Apply
                  </ThemedText>
                </Pressable>
              </View>
            </View>
          </View>
        </Modal>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  keyboardView: {
    flex: 1,
  },
  saveButton: {
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
    borderRadius: Radius.md,
    minWidth: 72,
    alignItems: 'center',
  },
  saveText: {
    color: '#fff',
    ...Typography.bodyBold,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: Spacing.lg,
  },
  photoSection: {
    alignItems: 'center',
    marginBottom: Spacing.xl,
  },
  photoContainer: {
    marginBottom: Spacing.md,
  },
  photo: {
    width: 120,
    height: 120,
    borderRadius: 60,
  },
  photoPlaceholder: {
    width: 120,
    height: 120,
    borderRadius: 60,
    borderWidth: 2,
    borderStyle: 'dashed',
    justifyContent: 'center',
    alignItems: 'center',
  },
  photoPlaceholderText: {
    fontSize: 40,
  },
  photoButtons: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  photoButton: {
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
    borderRadius: Radius.md,
  },
  photoButtonText: {
    color: '#fff',
    ...Typography.bodyBold,
  },
  section: {
    borderRadius: Radius.lg,
    borderWidth: 1,
    marginBottom: Spacing.md,
    overflow: 'hidden',
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: Spacing.lg,
    gap: Spacing.md,
  },
  sectionIconWrap: {
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sectionTitle: {
    flex: 1,
  },
  sectionContent: {
    padding: Spacing.lg,
    paddingTop: 0,
  },
  inputGroup: {
    marginBottom: Spacing.lg,
  },
  label: {
    ...Typography.bodyBold,
    marginBottom: Spacing.xs,
  },
  hint: {
    ...Typography.caption,
    marginBottom: Spacing.xs,
  },
  input: {
    ...Typography.body,
    lineHeight: 20,
    borderWidth: 1,
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.md,
    paddingVertical: 10,
    minHeight: 44,
  },
  inputSingleLine: {
    height: 44,
    paddingVertical: 0,
  },
  textArea: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  row: {
    flexDirection: 'row',
    gap: Spacing.md,
  },
  dateInputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: Radius.md,
    minHeight: 44,
    paddingHorizontal: Spacing.md,
  },
  dateInput: {
    flex: 1,
    ...Typography.body,
    lineHeight: 20,
    height: 44,
    paddingVertical: 0,
    paddingHorizontal: 0,
  },
  calendarHint: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingLeft: Spacing.sm,
    minHeight: 44,
    minWidth: 44,
  },
  dateModalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.35)',
    justifyContent: 'center',
    padding: Spacing.lg,
  },
  dateModalSheet: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    padding: Spacing.md,
    gap: Spacing.md,
  },
  dateModalTitle: {
    ...Typography.bodyBold,
  },
  dateModalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: Spacing.sm,
  },
  dateModalButton: {
    minHeight: 40,
    minWidth: 88,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.md,
  },
  dateModalButtonSecondary: {
    borderWidth: 1,
  },
  dateModalButtonText: {
    ...Typography.bodyBold,
  },
  halfWidth: {
    flex: 1,
  },
  buttonGroup: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  optionButton: {
    flex: 1,
    paddingVertical: Spacing.sm,
    borderRadius: Radius.md,
    borderWidth: 1,
    alignItems: 'center',
  },
  optionText: {
    ...Typography.body,
    fontWeight: '500',
  },
  chipGroup: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.sm,
  },
  chip: {
    paddingVertical: Spacing.xs,
    paddingHorizontal: Spacing.md,
    borderRadius: Radius.full,
    borderWidth: 1,
  },
  chipText: {
    ...Typography.body,
    fontWeight: '500',
  },
});
